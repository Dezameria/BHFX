package net.deizyn.bhfx.api;

/** How a running effect should end when it is stopped before its natural duration. */
public enum StopMode {
    /** Play the full ending (the spell finished normally). */
    FINISH,
    /** Play a short ending (the cast was interrupted). */
    CANCEL,
    /** Remove at once (dimension change, death, cleanup). */
    IMMEDIATE
}
