package dev.cmp.dynamics;

/**
 * Implemented by the {@code KineticNetwork} mixin. The version increments on
 * every topology, capacity, or member-stress change, allowing the manager to
 * cache K/C for sleeping networks.
 */
public interface SoftNetworkAccess {
    int cmp$getTopologyVersion();

    void cmp$bumpTopologyVersion();
}
