package com.cursecycle.client;

import com.cursecycle.network.TimingSettingsPayload;

/** Server settings must never be inferred from this client's local config on multiplayer servers. */
public final class ClientTimingSettings {
    private static TimingSettingsPayload snapshot;
    private static long revision;
    private ClientTimingSettings() {}
    public static void receive(TimingSettingsPayload value) { snapshot = value; revision++; }
    public static void reset() { snapshot = null; revision++; }
    public static TimingSettingsPayload get() { return snapshot; }
    public static long revision() { return revision; }
}
