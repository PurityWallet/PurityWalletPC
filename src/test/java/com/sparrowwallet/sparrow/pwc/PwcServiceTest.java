package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.Network;
import org.bitcoinpurity.pwc.provider.EndpointException;
import org.bitcoinpurity.pwc.provider.Prompt;
import org.bitcoinpurity.pwc.provider.PromptHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PwcServiceTest {
    @TempDir
    File dir;

    private final List<PwcService> services = new ArrayList<>();
    private String providerId;

    @BeforeEach
    void setUp() {
        Network.set(Network.REGTEST);
        providerId = "pwc-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void stopAll() {
        services.forEach(PwcService::stop);
    }

    private PwcService service() {
        Path endpoints = PwcPlatform.isWindows() ? null : dir.toPath().resolve("run");
        PromptHandler reject = Prompt::reject;
        PwcService service = new PwcService(new FakeHost(), new File(dir, "pwc"), endpoints, providerId, (s, backend) -> reject);
        services.add(service);
        return service;
    }

    @Test
    void providerIdsSeparateTheNetworks() {
        assertEquals("puritywallet", PwcService.providerId(Network.MAINNET));
        assertEquals("puritywallet-signet", PwcService.providerId(Network.SIGNET));
        assertEquals("puritywallet-regtest", PwcService.providerId(Network.REGTEST));
    }

    @Test
    void endpointProblemsAreDescribedForTheUser() {
        Path path = Path.of("/run/user/1000/pwc/puritywallet.sock");
        assertTrue(PwcService.describe(EndpointException.Kind.ALREADY_RUNNING, path, "").contains("Another PurityWallet instance"));
        assertTrue(PwcService.describe(EndpointException.Kind.NAME_TAKEN, path, "").contains("Another program"));
        assertTrue(PwcService.describe(EndpointException.Kind.UNSAFE_DIRECTORY, path, "").contains("closed to other users"));
        assertTrue(PwcService.describe(EndpointException.Kind.PATH_TOO_LONG, path, "").contains("too long"));
        assertTrue(PwcService.describe(EndpointException.Kind.IO, path, "Permission denied").endsWith("Permission denied"));
        for(EndpointException.Kind kind : EndpointException.Kind.values()) {
            assertTrue(PwcService.describe(kind, path, "").contains(path.toString()), kind.name());
        }
    }

    @Test
    @EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#hasNativeLibrary")
    void startsAndStopsWithoutSavingAuthorizationState() {
        PwcService service = service();
        service.start();
        assertEquals(PwcService.Status.RUNNING, service.getStatus(), service.getMessage());
        assertNull(service.getMessage());
        assertTrue(service.getEndpoint().toString().endsWith(providerId + (PwcPlatform.isWindows() ? "" : ".sock")), service.getEndpoint().toString());
        assertEquals(List.of(), service.pairedClients());

        service.stop();
        assertEquals(PwcService.Status.STOPPED, service.getStatus());
        assertNull(service.getEndpoint());
        assertArrayEquals(new String[] {PwcIds.SALT_FILE}, new File(dir, "pwc").list());

        service.start();
        assertEquals(PwcService.Status.RUNNING, service.getStatus(), service.getMessage());
        assertNull(service.getMessage());
    }

    @Test
    @EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#hasNativeLibrary")
    void aSecondInstanceReportsTheRunningOne() {
        service().start();
        PwcService second = service();
        second.start();
        assertEquals(PwcService.Status.FAILED, second.getStatus());
        assertTrue(second.getMessage().contains("already serves PWC") || second.getMessage().contains("Another program"), second.getMessage());
    }

    @Test
    @EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#hasNativeLibrary")
    void aLegacySnapshotIsRetiredAndPwcStartsFresh() throws Exception {
        File pwcDir = new File(dir, "pwc");
        PwcState.createDirectory(pwcDir);
        Path legacy = pwcDir.toPath().resolve(PwcState.LEGACY_SNAPSHOT);
        Files.createFile(legacy, PwcState.ownerOnly(legacy, false));
        Files.writeString(legacy, "legacy test snapshot");
        PwcService service = service();
        service.start();
        assertEquals(PwcService.Status.RUNNING, service.getStatus(), service.getMessage());
        assertTrue(service.pairedClients().isEmpty());
        assertTrue(service.grants().isEmpty());
        assertFalse(Files.exists(legacy));
        File[] kept = pwcDir.listFiles((d, name) -> name.startsWith(PwcState.LEGACY_SNAPSHOT + ".retired-"));
        assertEquals(1, kept.length);
        assertEquals("legacy test snapshot", Files.readString(kept[0].toPath()));
        service.stop();
        assertFalse(Files.exists(legacy));
    }

    @Test
    @EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#lacksNativeLibrary")
    void platformsWithoutTheNativeLibraryReportPwcAsUnavailable() {
        PwcService service = service();
        service.start();
        assertEquals(PwcService.Status.UNAVAILABLE, service.getStatus());
        assertTrue(service.getMessage().contains("not available on this platform"), service.getMessage());
        service.start();
        assertEquals(PwcService.Status.UNAVAILABLE, service.getStatus());
        service.stop();
    }
}
