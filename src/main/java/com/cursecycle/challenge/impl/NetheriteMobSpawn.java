package com.cursecycle.challenge.impl;

/** Per-entity birth state; persisted by the MobEntity Mixin, never a server-wide UUID cache. */
public interface NetheriteMobSpawn {
    void cursecycle$recordBirth(boolean eligible);
    void cursecycle$finishBirth();
}
