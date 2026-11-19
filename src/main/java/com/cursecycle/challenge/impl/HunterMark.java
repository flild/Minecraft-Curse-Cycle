package com.cursecycle.challenge.impl;

import java.util.UUID;

/** Persistent ownership, separate from NetheriteMobs' birth/equipment markers. */
public interface HunterMark {
    UUID cursecycle$hunterOwner();
    UUID cursecycle$hunterToken();
    long cursecycle$hunterActivation();
    void cursecycle$markHunter(UUID owner, UUID token, long activation);
}
