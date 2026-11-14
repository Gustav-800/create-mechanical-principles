package dev.cmp.config;

/**
 * Immutable, already-clamped view of the soft-dynamics configuration.
 *
 * <p>Keeping the solver configuration free of Minecraft/NeoForge types makes the
 * physics unit-testable and makes the runtime preset override straightforward.</p>
 */
public record DynamicsSettings(
        boolean enabled,
        float alpha,
        float accelCeilingUpMultiplier,
        float accelCeilingDownMultiplier,
        float accelFloorMultiplier,
        float stallRpmMultiplier,
        float stallRecoveryMultiplier,
        float overloadReleaseRatio,
        float kEpsilon,
        int sleepTicks,
        float sleepEpsilon,
        int maxComponentsPerTick,
        int topologyRebuildCooldown,
        float syncThreshold,
        int syncInterval,
        float clientInterpolation,
        boolean safeMode
) {
    public static final float MIN_ALPHA = 0.001F;
    public static final float MAX_ALPHA = 1.0F;

    public DynamicsSettings {
        alpha = clamp(alpha, MIN_ALPHA, MAX_ALPHA);
        accelCeilingUpMultiplier = Math.max(0.001F, accelCeilingUpMultiplier);
        accelCeilingDownMultiplier = Math.max(0.001F, accelCeilingDownMultiplier);
        accelFloorMultiplier = Math.max(0.0F, accelFloorMultiplier);
        float ceiling = Math.min(accelCeilingUpMultiplier, accelCeilingDownMultiplier);
        if (accelFloorMultiplier > ceiling) {
            accelFloorMultiplier = ceiling;
        }
        stallRpmMultiplier = clamp(stallRpmMultiplier, 0.0F, 1.0F);
        stallRecoveryMultiplier = Math.max(1.0F, stallRecoveryMultiplier);
        overloadReleaseRatio = clamp(overloadReleaseRatio, 0.50F, 1.0F);
        kEpsilon = Math.max(1.0E-6F, kEpsilon);
        sleepTicks = Math.max(1, sleepTicks);
        sleepEpsilon = Math.max(0.0F, sleepEpsilon);
        maxComponentsPerTick = Math.max(1, maxComponentsPerTick);
        topologyRebuildCooldown = Math.max(0, topologyRebuildCooldown);
        syncThreshold = Math.max(0.0F, syncThreshold);
        syncInterval = Math.max(1, syncInterval);
        clientInterpolation = clamp(clientInterpolation, 0.0F, 1.0F);
    }

    public float effectiveStallRecoveryThreshold() {
        return Math.max(stallRpmMultiplier, stallRpmMultiplier * stallRecoveryMultiplier);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
