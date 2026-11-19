package com.cursecycle.challenge.impl;

/** Entity-local provenance; no UUID registry or activation lifetime. */
public interface BuddingMark {
    boolean cursecycle$isBuddingChild();
    void cursecycle$markBuddingChild();
    boolean cursecycle$claimBuddingDeath();
}
