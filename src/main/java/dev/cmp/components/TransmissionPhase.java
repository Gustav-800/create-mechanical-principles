package dev.cmp.components;

/**
 * Device-level phases exposed by the soft transmission controllers. These are
 * intentionally separate from Create's block state; the block state remains
 * authoritative for rendering and redstone behaviour.
 */
public enum TransmissionPhase {
    DISENGAGED,
    SOFT_START,
    RAMPING,
    SYNCED,
    FORWARD,
    REVERSING,
    REVERSED,
    RETURNING,
    STARTING,
    RUNNING,
    IDLE
}
