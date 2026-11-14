package dev.cmp.dynamics;

import dev.cmp.config.DynamicsSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDynamicsCoreTest {
    private static final DynamicsSettings SETTINGS = new DynamicsSettings(
            true, 0.05F, 0.20F, 0.30F, 0.01F,
            0.10F, 1.5F, 0.95F, 1.0E-4F,
            40, 0.01F, 8, 10, 0.1F, 20, 0.3F, false);

    @Test
    void overloadSettlesAtEquilibriumInsteadOfStopping() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(8.0F);
        SoftDynamicsCore.Inputs inputs = new SoftDynamicsCore.Inputs(128.0F, 160.0F, 8.0F, true);

        for (int i = 0; i < 300; i++) {
            SoftDynamicsCore.step(state, inputs, SETTINGS);
        }

        assertEquals(0.8F, state.omega(), 0.01F);
        assertTrue(state.overloadLatched());
        assertFalse(state.stalled());
        assertEquals(SoftDynamicsCore.Zone.OVERLOADED, state.zone());
    }

    @Test
    void severeOverloadStallsAndRecoversWithHysteresis() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(8.0F);
        SoftDynamicsCore.Inputs overloaded = new SoftDynamicsCore.Inputs(128.0F, 1600.0F, 8.0F, true);

        for (int i = 0; i < 300; i++) {
            SoftDynamicsCore.step(state, overloaded, SETTINGS);
        }
        assertTrue(state.stalled());
        assertEquals(0.0F, state.omega(), 1.0E-5F);
        assertEquals(SoftDynamicsCore.Zone.STALLED, state.zone());

        SoftDynamicsCore.Inputs recovered = new SoftDynamicsCore.Inputs(128.0F, 8.0F, 8.0F, true);
        for (int i = 0; i < 200; i++) {
            SoftDynamicsCore.step(state, recovered, SETTINGS);
        }
        assertFalse(state.stalled());
        assertTrue(state.omega() > 7.9F);
    }

    @Test
    void overloadLatchUsesReleaseRatio() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(10.0F);
        SoftDynamicsCore.step(state, new SoftDynamicsCore.Inputs(100.0F, 10.0F, 10.0F, true), SETTINGS);
        assertTrue(state.overloadLatched());

        // 100 SU is still above 95% of 105 SU; the latch must stay closed.
        SoftDynamicsCore.step(state, new SoftDynamicsCore.Inputs(105.0F, 10.0F, 10.0F, true), SETTINGS);
        assertTrue(state.overloadLatched());

        // At 100 SU and 120 SU capacity, 100 < 114, so it releases.
        SoftDynamicsCore.step(state, new SoftDynamicsCore.Inputs(120.0F, 10.0F, 10.0F, true), SETTINGS);
        assertFalse(state.overloadLatched());
    }

    @Test
    void upwardAccelerationRespectsCeiling() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(0.0F);
        SoftDynamicsCore.StepResult result = SoftDynamicsCore.step(
                state, new SoftDynamicsCore.Inputs(128.0F, 0.0F, 8.0F, true), SETTINGS);
        assertEquals(1.6F, result.omega(), 1.0E-4F);
    }

    @Test
    void nonZeroAccelerationRespectsFloor() {
        DynamicsSettings slow = new DynamicsSettings(
                true, 0.001F, 0.20F, 0.30F, 0.01F,
                0.10F, 1.5F, 0.95F, 1.0E-4F,
                40, 0.01F, 8, 10, 0.1F, 20, 0.3F, false);
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(5.0F);
        SoftDynamicsCore.StepResult result = SoftDynamicsCore.step(
                state, new SoftDynamicsCore.Inputs(100.0F, 10.0F, 10.0F, true), slow);
        assertEquals(5.1F, result.omega(), 1.0E-3F);
    }

    @Test
    void equilibriumEntersSleepAfterConfiguredTicks() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(8.0F);
        SoftDynamicsCore.Inputs inputs = new SoftDynamicsCore.Inputs(128.0F, 0.0F, 8.0F, true);
        for (int i = 0; i < 50; i++) {
            SoftDynamicsCore.step(state, inputs, SETTINGS);
        }
        assertEquals(SoftDynamicsCore.Zone.SLEEPING, state.zone());
        assertTrue(state.sleepTicks() >= 40);
    }

    @Test
    void zeroCapacityStopsTheNetwork() {
        SoftDynamicsCore.State state = new SoftDynamicsCore.State();
        state.seed(8.0F);
        for (int i = 0; i < 100; i++) {
            SoftDynamicsCore.step(state, new SoftDynamicsCore.Inputs(0.0F, 10.0F, 8.0F, true), SETTINGS);
        }
        assertEquals(0.0F, state.omega(), 1.0E-5F);
        assertTrue(state.stalled());
    }
}
