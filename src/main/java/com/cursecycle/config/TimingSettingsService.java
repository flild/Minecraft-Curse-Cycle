package com.cursecycle.config;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.network.TimingSettingsPayload;
import com.cursecycle.network.TimingSettingsRequestPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.minecraft.server.network.ServerPlayerEntity;

/** Invoked on the server thread. Check permissions again for every write. */
public final class TimingSettingsService {
    private TimingSettingsService() {}

    public static boolean canEdit(ServerPlayerEntity player) {
        var server = player.getServer();
        return player.hasPermissionLevel(2) || server.isSingleplayer() && server.isHost(player.getGameProfile());
    }

    public static int handle(ServerPlayerEntity player, TimingSettingsRequestPayload request) {
        if (!request.save()) return TimingSettingsPayload.SNAPSHOT;
        if (!canEdit(player)) return TimingSettingsPayload.DENIED;
        TimingSettings next = request.settings();
        if (!next.isValid()) return TimingSettingsPayload.INVALID;
        var config = CurseCycleMod.getConfig();
        TimingSettings old = TimingSettings.from(config);
        if (old.equals(next)) return TimingSettingsPayload.SAVED;
        next.applyTo(config);
        try {
            config.saveOrThrow();
        } catch (java.io.IOException failure) {
            old.applyTo(config);
            CurseCycleMod.LOGGER.error("Could not save timing settings requested by {}", player.getGameProfile().getName(), failure);
            return TimingSettingsPayload.SAVE_FAILED;
        }
        var server = player.getServer();
        var state = CurseCycleState.getServerState(server);
        CycleManager.applyTimingChange(state, old, next, server.getOverworld().getTimeOfDay());
        CycleManager.syncState(server, state, config);
        CurseCycleMod.LOGGER.info("{} changed Curse-Cycle timing: {} -> {}", player.getGameProfile().getName(), old, next);
        return TimingSettingsPayload.SAVED;
    }

    public static TimingSettingsPayload snapshot(ServerPlayerEntity player, int result) {
        var settings = TimingSettings.from(CurseCycleMod.getConfig());
        return new TimingSettingsPayload(settings.intervalDays(), settings.noticeDays(), settings.preparationSeconds(),
                canEdit(player), result);
    }
}
