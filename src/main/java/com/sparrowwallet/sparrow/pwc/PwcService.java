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
import java.util.concurrent.RejectedExecutionException;
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

    private PwcState.Session stateSession;
    private PwcWalletBackend backend;
    private Provider provider;
    private LocalServer server;
    private PwcEventForwarder forwarder;
    private volatile ScheduledExecutorService executor;
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
            stateSession = state.openSession();
            boolean interrupted = stateSession.interrupted();
            backend = new PwcWalletBackend(host, PwcIds.load(dir));
            Config config = Config.defaults().toBuilder().providerName(SparrowWallet.APP_NAME).providerVersion(SparrowWallet.APP_VERSION).build();
            PromptHandler handler = prompts.apply(this, backend);
            byte[] snapshot = interrupted ? null : state.load();
            if(interrupted) {
                message = "PWC stopped unexpectedly. Pair applications again to restore access.";
                log.warn(message);
            }
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
            stateSession.markRunning();
            try {
                server = endpointDirectory == null ? LocalServer.start(provider, providerId) : LocalServer.start(provider, endpointDirectory, providerId);
            } catch(EndpointException | RuntimeException | LinkageError e) {
                if(!interrupted) {
                    stateSession.markStopped();
                }
                throw e;
            }
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "pwc-events");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleWithFixedDelay(backend::expireApprovals, 1, 1, TimeUnit.SECONDS);
            Provider notified = provider;
            ScheduledExecutorService events = executor;
            forwarder = new PwcEventForwarder(backend.getIds(), host::openWalletForms, event -> {
                try {
                    events.execute(() -> notifyIfRunning(notified, event));
                } catch(RejectedExecutionException e) {
                    // The service is stopping.
                }
            });
            EventManager.get().register(forwarder);
            status = Status.RUNNING;
            running = true;
            log.info("PWC provider listening on " + server.path());
        } catch(LinkageError e) {
            status = Status.UNAVAILABLE;
            message = "PWC is not available on this platform: the PWC SDK has no native library for " + platform() + ".";
            log.info(message, e);
            closeResources();
        } catch(EndpointException e) {
            status = Status.FAILED;
            message = describe(e.kind(), e.path(), e.getMessage());
            log.warn(message);
            closeResources();
        } catch(IOException | RuntimeException e) {
            status = Status.FAILED;
            message = "PWC could not start: " + e.getMessage();
            log.error(message, e);
            closeResources();
        }
    }

    public synchronized void stop() {
        if(status != Status.RUNNING) {
            return;
        }
        running = false;
        EventManager.get().unregister(forwarder);
        backend.close();
        server.close();
        server = null;
        boolean saved = save();

        // SDK 0.2.1 leaves existing connections open when the listener closes.
        for(PairedClient client : provider.pairedClients()) {
            provider.unpairClient(client.clientId());
        }
        if(saved) {
            try {
                stateSession.markStopped();
                message = null;
            } catch(IOException e) {
                stateSaveFailed(e);
            }
        }
        closeResources();
        status = Status.STOPPED;
    }

    private synchronized void notifyIfRunning(Provider notified, BackendEvent event) {
        if(running && provider == notified) {
            notified.notify(event);
        }
    }

    private void closeResources() {
        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if(server != null) {
            server.close();
            server = null;
        }
        if(backend != null) {
            backend.close();
            backend = null;
        }
        if(provider != null) {
            provider.close();
            provider = null;
        }
        if(stateSession != null) {
            try {
                stateSession.close();
            } catch(IOException e) {
                log.warn("Could not close the PWC state file", e);
            }
            stateSession = null;
        }
    }

    private void stateSaveFailed(IOException e) {
        message = "Could not save PWC pairing and permission changes. If PurityWallet closes before saving succeeds, applications must pair again.";
        log.error("Could not save the PWC pairings", e);
    }

    public synchronized boolean save() {
        if(provider == null) {
            return false;
        }
        try {
            state.save(provider.saveState());
            return true;
        } catch(IOException e) {
            stateSaveFailed(e);
            return false;
        }
    }

    /** Saves shortly after a decision in a prompt, once the Provider has applied it. */
    void saveSoon() {
        ScheduledExecutorService current = executor;
        if(current != null) {
            try {
                current.schedule(() -> {
                    synchronized(this) {
                        if(executor == current) {
                            save();
                        }
                    }
                }, 2, TimeUnit.SECONDS);
            } catch(RejectedExecutionException e) {
                // The service is stopping.
            }
        }
    }

    public synchronized List<PairedClient> pairedClients() {
        return provider == null ? List.of() : provider.pairedClients();
    }

    public synchronized List<Grant> grants() {
        return provider == null ? List.of() : provider.grants();
    }

    public synchronized boolean revokeGrant(String grantId) {
        return provider != null && provider.revokeGrant(grantId) && save();
    }

    public synchronized boolean unpairClient(String clientId) {
        if(provider == null) {
            return false;
        }
        provider.unpairClient(clientId);
        return save();
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
