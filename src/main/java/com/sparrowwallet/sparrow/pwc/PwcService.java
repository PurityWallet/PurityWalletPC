package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.bitcoinpurity.pwc.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/** Runs the PWC LOCAL Provider for the wallets open in this application. */
public class PwcService {
    private static final Logger log = LoggerFactory.getLogger(PwcService.class);

    public enum Status { STOPPED, RUNNING, FAILED, UNAVAILABLE }

    private final PwcHost host;
    private final File dir;
    private final Path endpointDirectory;
    private final String providerId;
    private final BiFunction<PwcService, PwcWalletBackend, PromptHandler> prompts;
    private final PwcState state;

    private PwcWalletBackend backend;
    private Provider provider;
    private LocalServer server;
    private PwcEventForwarder forwarder;
    private ScheduledExecutorService executor;
    private Status status = Status.STOPPED;
    private volatile boolean running;
    private String message;

    /** {@code endpointDirectory} null uses the platform's default endpoint. */
    public PwcService(PwcHost host, File dir, Path endpointDirectory, String providerId, BiFunction<PwcService, PwcWalletBackend, PromptHandler> prompts) {
        this.host = host;
        this.dir = dir;
        this.endpointDirectory = endpointDirectory;
        this.providerId = providerId;
        this.prompts = prompts;
        this.state = new PwcState(dir);
    }

    public static String providerId(Network network) {
        return network == Network.MAINNET ? "puritywallet" : "puritywallet-" + network.getName();
    }

    static String platform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");
        return (os.startsWith("mac") ? "macos" : os.startsWith("windows") ? "windows" : os) + "-" + ("amd64".equals(arch) ? "x86_64" : "arm64".equals(arch) ? "aarch64" : arch);
    }

    static String describe(EndpointException.Kind kind, Path path, String detail) {
        return switch(kind) {
            case ALREADY_RUNNING -> "Another PurityWallet instance already serves PWC at " + path + ".";
            case NAME_TAKEN -> "Another program uses the PWC pipe name " + path + ", so PWC stays off.";
            case UNSAFE_DIRECTORY -> "The PWC endpoint directory " + path + " must belong to you and be closed to other users.";
            case PATH_TOO_LONG -> "The PWC socket path " + path + " is too long for this system.";
            case IO -> "PWC could not open its endpoint at " + path + ": " + detail;
        };
    }

    static String describe(StateException.Kind kind, File kept) {
        String reason = kind == StateException.Kind.UNSUPPORTED_VERSION ? "were saved by a newer version" : "could not be read";
        return "The saved PWC pairings " + reason + ". They were kept in " + kept + " and PWC started without them.";
    }

    public synchronized void start() {
        if(status == Status.RUNNING || status == Status.UNAVAILABLE) {
            return;
        }
        message = null;
        try {
            backend = new PwcWalletBackend(host, PwcIds.load(dir));
            Config config = Config.defaults().toBuilder().providerName(SparrowWallet.APP_NAME).providerVersion(SparrowWallet.APP_VERSION).build();
            PromptHandler handler = prompts.apply(this, backend);
            byte[] snapshot = state.load();
            if(snapshot == null) {
                provider = Provider.create(backend, config, handler);
            } else {
                try {
                    provider = Provider.withState(backend, config, handler, snapshot);
                } catch(StateException e) {
                    message = describe(e.kind(), state.quarantine());
                    log.warn(message);
                    provider = Provider.create(backend, config, handler);
                }
            }
            server = endpointDirectory == null ? LocalServer.start(provider, providerId) : LocalServer.start(provider, endpointDirectory, providerId);
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "pwc-events");
                thread.setDaemon(true);
                return thread;
            });
            Provider notified = provider;
            forwarder = new PwcEventForwarder(backend.getIds(), host::openWalletForms, event -> executor.execute(() -> notified.notify(event)));
            EventManager.get().register(forwarder);
            status = Status.RUNNING;
            running = true;
            log.info("PWC provider listening on " + server.path());
        } catch(LinkageError e) {
            status = Status.UNAVAILABLE;
            message = "PWC is not available on this platform: the PWC SDK has no native library for " + platform() + ".";
            log.info(message, e);
            closeProvider();
        } catch(EndpointException e) {
            status = Status.FAILED;
            message = describe(e.kind(), e.path(), e.getMessage());
            log.warn(message);
            closeProvider();
        } catch(IOException | RuntimeException e) {
            status = Status.FAILED;
            message = "PWC could not start: " + e.getMessage();
            log.error(message, e);
            closeProvider();
        }
    }

    public synchronized void stop() {
        if(status != Status.RUNNING) {
            return;
        }
        running = false;
        save();
        EventManager.get().unregister(forwarder);
        server.close();
        server = null;
        executor.shutdown();
        closeProvider();
        status = Status.STOPPED;
        message = null;
    }

    private void closeProvider() {
        if(provider != null) {
            provider.close();
            provider = null;
        }
    }

    public synchronized void save() {
        if(provider == null) {
            return;
        }
        try {
            state.save(provider.saveState());
        } catch(IOException e) {
            log.error("Could not save the PWC pairings", e);
        }
    }

    /** Saves shortly after a decision in a prompt, once the Provider has applied it. */
    void saveSoon() {
        ScheduledExecutorService current = executor;
        if(current != null && !current.isShutdown()) {
            current.schedule(this::save, 2, TimeUnit.SECONDS);
        }
    }

    public synchronized List<PairedClient> pairedClients() {
        return provider == null ? List.of() : provider.pairedClients();
    }

    public synchronized List<Grant> grants() {
        return provider == null ? List.of() : provider.grants();
    }

    public synchronized boolean revokeGrant(String grantId) {
        boolean revoked = provider != null && provider.revokeGrant(grantId);
        save();
        return revoked;
    }

    public synchronized void unpairClient(String clientId) {
        if(provider != null) {
            provider.unpairClient(clientId);
            save();
        }
    }

    /** Read without the service lock, by threads that wait on the user. */
    boolean isRunning() {
        return running;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized String getMessage() {
        return message;
    }

    public synchronized Path getEndpoint() {
        return server == null ? null : server.path();
    }

    public PwcWalletBackend getBackend() {
        return backend;
    }
}
