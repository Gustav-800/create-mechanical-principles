package dev.cmp.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequencerPlanTest {
    @Test
    void longDistanceUsesTrapezoid() {
        SequencerPlan plan = SequencerPlan.plan(8.0F, 500.0F, 0.5F, 0.3F);
        assertEquals(SequencerPlan.Shape.TRAPEZOID, plan.shape());
        assertEquals(8.0F, plan.peakRpm(), 1.0E-4F);
        assertEquals(16.0F, plan.accelerationTicks(), 1.0E-4F);
        assertTrue(plan.cruiseTicks() > 0.0F);
        assertEquals(2.0F * plan.accelerationTicks() + plan.cruiseTicks(),
                plan.totalTicks(), 1.0E-3F);
    }

    @Test
    void shortDistanceUsesTriangleWithoutOvershoot() {
        SequencerPlan plan = SequencerPlan.plan(8.0F, 10.0F, 0.5F, 0.3F);
        assertEquals(SequencerPlan.Shape.TRIANGLE, plan.shape());
        assertEquals(0.0F, plan.cruiseTicks(), 1.0E-6F);
        assertTrue(plan.peakRpm() < 8.0F);
        assertEquals(2.0F * plan.peakRpm() / 0.5F, plan.totalTicks(), 1.0E-3F);
    }

    @Test
    void zeroDistanceIsEmpty() {
        assertEquals(SequencerPlan.Shape.EMPTY, SequencerPlan.plan(8.0F, 0.0F, 0.5F, 0.3F).shape());
    }
}
