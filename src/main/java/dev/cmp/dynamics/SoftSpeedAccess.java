package dev.cmp.dynamics;

/**
 * Implemented by the {@code KineticBlockEntity} mixin so the manager can read
 * and write Create's speed with knowledge of the unthrottled (nominal) speed.
 */
public interface SoftSpeedAccess {
    float cmp$getNominalSpeed();

    void cmp$setNominalSpeed(float nominalSpeed);

    float cmp$getStoredSpeed();

    /**
     * Writes a display speed without clobbering the nominal speed. This is the
     * only speed write performed by the soft-dynamics manager.
     */
    void cmp$applyDisplaySpeed(float displaySpeed);

    boolean cmp$isApplyingSoftSpeed();
}
