package com.cursecycle.diagnostics;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StartupDiagnosticsTest {
    @TempDir Path directory;

    @Test
    void declaredPreLaunchEntrypointHasPublicNoArgumentConstructor() throws Exception {
        try (var input = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(input);
            var metadata = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var entrypoints = metadata.getAsJsonObject("entrypoints").getAsJsonArray("preLaunch");
            assertFalse(entrypoints.isEmpty());
            for (var entrypoint : entrypoints) {
                Class<?> type = Class.forName(entrypoint.getAsString());
                assertInstanceOf(PreLaunchEntrypoint.class, type.getConstructor().newInstance());
            }
        }
    }

    @Test
    void recordsStageAndCauseAndRethrowsOriginalFailure() throws Exception {
        Path report = directory.resolve("logs/cursecycle-startup-error.txt");
        var failure = new IllegalStateException("Registration failed", new NoClassDefFoundError("MissingDependency"));

        var thrown = assertThrows(IllegalStateException.class, () ->
                StartupDiagnostics.run("client", report, diagnostics -> {
                    diagnostics.stage("registering HUD renderer");
                    throw failure;
                }));

        assertSame(failure, thrown);
        String text = Files.readString(report);
        assertTrue(text.contains("Phase: client"));
        assertTrue(text.contains("Stage: registering HUD renderer"));
        assertTrue(text.contains("Registration failed"));
        assertTrue(text.contains("Caused by: java.lang.NoClassDefFoundError: MissingDependency"));
    }

    @Test
    void reportWriteFailureDoesNotMaskOriginalLinkageError() throws Exception {
        Path blockedDirectory = directory.resolve("blocked");
        Files.writeString(blockedDirectory, "a file, not a directory");
        var failure = new NoClassDefFoundError("MissingDependency");

        assertSame(failure, assertThrows(NoClassDefFoundError.class, () ->
                StartupDiagnostics.run("common", blockedDirectory.resolve("report.txt"), diagnostics -> {
                    throw failure;
                })));
    }

    @Test
    void successfulInitializationDoesNotCreateErrorReport() {
        Path report = directory.resolve("logs/cursecycle-startup-error.txt");
        StartupDiagnostics.run("common", report, diagnostics -> diagnostics.stage("registering challenges"));
        assertFalse(Files.exists(report));
    }
}
