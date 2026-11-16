package com.cursecycle.diagnostics;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

/** Reports initialization failures without replacing Fabric's crash handling. */
public final class StartupDiagnostics implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("cursecycle");
    private final String phase;
    private String stage = "starting";

    /** Fabric's default language adapter instantiates the preLaunch entrypoint reflectively. */
    public StartupDiagnostics() {
        this("preLaunch");
    }

    private StartupDiagnostics(String phase) {
        this.phase = phase;
    }

    @Override
    public void onPreLaunch() {
        initialize("preLaunch", diagnostics -> {
            diagnostics.stage("runtime information");
            FabricLoader loader = FabricLoader.getInstance();
            LOGGER.info("[Curse-Cycle] Runtime: mod={}, Minecraft={}, Fabric Loader={}, Fabric API={}, Java={} ({})",
                    version("cursecycle"), version("minecraft"), version("fabricloader"), version("fabric-api"),
                    System.getProperty("java.version"), System.getProperty("java.vendor"));
            LOGGER.info("[Curse-Cycle] Environment={}, development={}, game directory={}, config={}",
                    loader.getEnvironmentType(), loader.isDevelopmentEnvironment(), loader.getGameDir(),
                    loader.getConfigDir().resolve("cursecycle.json"));
        });
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("missing");
    }

    public static void initialize(String phase, Consumer<StartupDiagnostics> action) {
        run(phase, FabricLoader.getInstance().getGameDir().resolve("logs/cursecycle-startup-error.txt"), action);
    }

    static void run(String phase, Path errorReport, Consumer<StartupDiagnostics> action) {
        StartupDiagnostics diagnostics = new StartupDiagnostics(phase);
        try {
            action.accept(diagnostics);
            LOGGER.info("[Curse-Cycle] {} initialization complete", phase);
        } catch (RuntimeException | Error failure) {
            LOGGER.error("[Curse-Cycle] Initialization failed: phase={}, stage={}. "
                    + "See logs/latest.log and crash-reports for the full launch context.",
                    phase, diagnostics.stage, failure);
            try {
                Files.createDirectories(errorReport.getParent());
                try (var writer = new PrintWriter(Files.newBufferedWriter(errorReport, StandardCharsets.UTF_8))) {
                    writer.println("Curse-Cycle initialization failure at " + Instant.now());
                    writer.println("Phase: " + phase);
                    writer.println("Stage: " + diagnostics.stage);
                    writer.println("Java: " + System.getProperty("java.version"));
                    writer.println("Full runtime information: logs/latest.log, search for [Curse-Cycle] Runtime");
                    failure.printStackTrace(writer);
                    if (writer.checkError()) throw new IOException("Could not write the complete startup error report");
                }
                LOGGER.error("[Curse-Cycle] Startup error report: {}", errorReport);
            } catch (IOException | RuntimeException reportFailure) {
                LOGGER.error("[Curse-Cycle] Could not write startup error report to {}", errorReport, reportFailure);
            }
            throw failure;
        }
    }

    public void stage(String stage) {
        this.stage = stage;
        LOGGER.info("[Curse-Cycle] {}: {}", phase, stage);
    }
}
