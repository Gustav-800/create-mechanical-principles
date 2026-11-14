package dev.cmp.components;

/**
 * Pure distance/speed plan for a sequenced gearshift step.
 *
 * <p>Create's sequencer works in degrees (TURN_ANGLE) or blocks
 * (TURN_DISTANCE). {@code displacementPerRpm} is the distance covered per tick
 * at 1 RPM: 0.3 degrees for angles and 1/512 blocks for distances. The planner
 * produces a trapezoid when there is enough distance to reach target speed and
 * a triangle otherwise. It is intentionally side-effect free so the shape can
 * be unit tested.</p>
 */
public record SequencerPlan(
        Shape shape,
        float targetRpm,
        float peakRpm,
        float accelerationTicks,
        float cruiseTicks,
        float totalTicks
) {
    public enum Shape {
        TRAPEZOID,
        TRIANGLE,
        EMPTY
    }

    public static SequencerPlan empty() {
        return new SequencerPlan(Shape.EMPTY, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static SequencerPlan plan(float targetRpm, float distance, float accelerationRpmPerTick,
                                     float displacementPerRpm) {
        float target = Math.abs(targetRpm);
        float accel = Math.max(1.0E-4F, accelerationRpmPerTick);
        float k = Math.max(1.0E-6F, displacementPerRpm);
        if (target <= 1.0E-4F || distance <= 1.0E-4F) {
            return empty();
        }

        float tAccel = target / accel;
        float tCruise = distance * k / target - tAccel;
        if (tCruise >= 0.0F) {
            float total = 2.0F * tAccel + tCruise;
            return new SequencerPlan(Shape.TRAPEZOID, target, target, tAccel, tCruise, total);
        }

        float peak = (float) Math.sqrt(distance * k * accel);
        float tAccelTriangle = peak / accel;
        return new SequencerPlan(Shape.TRIANGLE, target, peak, tAccelTriangle, 0.0F,
                2.0F * tAccelTriangle);
    }
}
