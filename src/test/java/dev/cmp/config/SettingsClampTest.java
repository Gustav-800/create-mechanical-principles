package dev.cmp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsClampTest {
    @Test
    void dynamicsClampsCrossFieldConstraints() {
        DynamicsSettings settings = new DynamicsSettings(
                true, 0.0F, 0.05F, 0.02F, 0.50F,
                2.0F, 0.25F, 2.0F, 0.0F,
                0, -1.0F, 0, -4, -1.0F, 0, 5.0F, false);
        assertEquals(DynamicsSettings.MIN_ALPHA, settings.alpha(), 1.0E-6F);
        assertEquals(0.02F, settings.accelFloorMultiplier(), 1.0E-6F);
        assertEquals(1.0F, settings.stallRpmMultiplier(), 1.0E-6F);
        assertTrue(settings.stallRecoveryMultiplier() >= 1.0F);
        assertEquals(1.0F, settings.overloadReleaseRatio(), 1.0E-6F);
        assertTrue(settings.kEpsilon() >= 1.0E-6F);
        assertEquals(1, settings.sleepTicks());
        assertEquals(1, settings.maxComponentsPerTick());
        assertEquals(0, settings.topologyRebuildCooldown());
        assertEquals(1, settings.syncInterval());
        assertEquals(1.0F, settings.clientInterpolation(), 1.0E-6F);
    }

    @Test
    void componentThresholdsAreBounded() {
        ComponentSettings settings = new ComponentSettings(
                true, -3, 0.0F, 0.0F, true, 9.0F, -2, true,
                0.0F, -4, -2.0F, false, -1.0F, true, 999, false);
        assertEquals(0, settings.clutchEngageDelayTicks());
        assertTrue(settings.clutchAccelFraction() > 0.0F);
        assertTrue(settings.syncEpsilon() > 0.0F);
        assertEquals(3.0F, settings.reverseAccelMultiplier(), 1.0E-6F);
        assertEquals(0, settings.reverseTransitionTicks());
        assertTrue(settings.sequencerAcceleration() > 0.0F);
        assertEquals(0.0F, settings.overloadReplanThreshold(), 1.0E-6F);
        assertTrue(settings.chainMaxRatioDelta() > 0.0F);
        assertEquals(256, settings.hotPlugSpeedThreshold());
        assertTrue(settings.hotPlugUnlimited());
    }
}
