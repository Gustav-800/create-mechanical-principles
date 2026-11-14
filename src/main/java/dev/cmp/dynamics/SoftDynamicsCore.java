package dev.cmp.dynamics;

import dev.cmp.config.DynamicsSettings;

/**
 * Pure soft-dynamics solver for one kinetic network.
 *
 * <p>All quantities use Create's native units: speed in RPM and stress in SU.
 * For a network, {@code K} is the equivalent stress consumption per RPM:
 * {@code K = sum(baseStress_i * |ratedSpeed_i|) / ratedSpeed}. Therefore the
 * actual consumed stress at a throttled network speed is {@code S = K * omega}.
 * The equilibrium speed is {@code omegaEq = C / K} and the instantaneous
 * acceleration is {@code alpha * (C - S) / K}, clamped by the configured
 * ceilings. This is the single formula used for spin-up, spin-down, overload,
 * and stall recovery.</p>
 *
 * <p>This class deliberately has no Minecraft dependency so that the complete
 * state machine can be exercised by fast unit tests.</p>
 */
public final class SoftDynamicsCore {
    public static final float EPSILON = 1.0E-5F;
    private static final float TARGET_EPSILON = 1.0E-4F;

    public enum Zone {
        STEADY,
        ACCELERATING,
        DECELERATING,
        OVERLOADED,
        STALLED,
        SLEEPING
    }

    /**
     * @param capacity           total stress capacity C in SU
     * @param stressCoefficient  equivalent stress consumption K in SU/RPM
     * @param ratedSpeed         the no-load source speed omega0 in RPM
     * @param hasSource          whether the network still has a rotational source
     */
    public record Inputs(float capacity, float stressCoefficient, float ratedSpeed, boolean hasSource) {
        public Inputs {
            capacity = Math.max(0.0F, capacity);
            stressCoefficient = Math.max(0.0F, stressCoefficient);
            ratedSpeed = Math.max(0.0F, ratedSpeed);
        }
    }

    public record StepResult(
            float omega,
            float targetOmega,
            float acceleration,
            float stress,
            float equilibriumOmega,
            boolean overloadLatched,
            boolean stalled,
            Zone zone,
            boolean changed,
            int sleepTicks
    ) {
    }

    /** Mutable per-network state. */
    public static final class State {
        private float omega;
        private boolean overloadLatched;
        private boolean stalled;
        private int sleepTicks;
        private Zone zone = Zone.STEADY;
        private boolean initialised;

        public float omega() {
            return omega;
        }

        public boolean overloadLatched() {
            return overloadLatched;
        }

        public boolean stalled() {
            return stalled;
        }

        public int sleepTicks() {
            return sleepTicks;
        }

        public Zone zone() {
            return zone;
        }

        public boolean initialised() {
            return initialised;
        }

        public void seed(float omega) {
            this.omega = Math.max(0.0F, omega);
            this.initialised = true;
            this.sleepTicks = 0;
            this.zone = Zone.STEADY;
        }

        public void reset() {
            this.omega = 0.0F;
            this.overloadLatched = false;
            this.stalled = false;
            this.sleepTicks = 0;
            this.zone = Zone.STEADY;
            this.initialised = false;
        }
    }

    private SoftDynamicsCore() {
    }

    public static StepResult step(State state, Inputs inputs, DynamicsSettings settings) {
        float previous = state.omega;
        boolean wasLatched = state.overloadLatched;
        boolean wasStalled = state.stalled;
        Zone previousZone = state.zone;

        if (!state.initialised) {
            state.seed(inputs.ratedSpeed());
        }

        float omega0 = inputs.ratedSpeed();
        float capacity = inputs.capacity();
        float k = inputs.stressCoefficient();
        float omega = state.omega;

        if (!inputs.hasSource() || omega0 <= EPSILON) {
            state.overloadLatched = false;
            state.stalled = true;
            state.omega = 0.0F;
            state.sleepTicks = 0;
            state.zone = Zone.STALLED;
            boolean changed = previous > EPSILON || wasLatched || !wasStalled || previousZone != Zone.STALLED;
            return result(state, 0.0F, 0.0F, 0.0F, 0.0F, false, true, changed);
        }

        if (k <= settings.kEpsilon()) {
            // No load: the network may return to its rated speed without stress.
            state.overloadLatched = false;
            state.stalled = false;
            float target = omega0;
            float step = Math.min(Math.abs(target - omega), settings.accelCeilingUpMultiplier() * omega0);
            state.omega = approach(omega, target, step);
            state.zone = zoneFor(state, target, false, previousZone, settings);
            updateSleep(state, target, settings);
            boolean changed = Math.abs(state.omega - previous) > EPSILON
                    || wasLatched || wasStalled || previousZone != state.zone;
            return result(state, target, step, 0.0F, Float.POSITIVE_INFINITY, false, false, changed);
        }

        float stress = k * omega;
        float releaseThreshold = capacity * settings.overloadReleaseRatio();

        if (stress >= capacity) {
            state.overloadLatched = true;
        } else if (state.overloadLatched && stress < releaseThreshold) {
            state.overloadLatched = false;
        }

        float omegaEq = capacity / k;
        float stallThreshold = settings.stallRpmMultiplier() * omega0;

        if (!state.stalled && state.overloadLatched && omegaEq < stallThreshold) {
            state.stalled = true;
        } else if (state.stalled) {
            float recoveryThreshold = settings.effectiveStallRecoveryThreshold() * omega0;
            if (omegaEq > recoveryThreshold) {
                state.stalled = false;
            }
        }

        float target;
        if (state.stalled) {
            target = 0.0F;
        } else if (state.overloadLatched) {
            target = Math.min(omega0, omegaEq);
        } else {
            target = omega0;
        }

        float error = target - omega;
        float acceleration = 0.0F;

        if (Math.abs(error) > TARGET_EPSILON) {
            float raw = settings.alpha() * (capacity - stress) / Math.max(k, settings.kEpsilon());
            float ceiling = error > 0.0F
                    ? settings.accelCeilingUpMultiplier() * omega0
                    : settings.accelCeilingDownMultiplier() * omega0;
            float magnitude = Math.min(Math.abs(raw), ceiling);
            float floor = settings.accelFloorMultiplier() * omega0;
            if (magnitude < floor) {
                magnitude = floor;
            }
            magnitude = Math.min(magnitude, Math.abs(error));
            float signed = Math.copySign(magnitude, error);
            omega += signed;
            acceleration = signed;
        }

        if (Math.abs(target - omega) <= TARGET_EPSILON) {
            omega = target;
            acceleration = 0.0F;
            state.sleepTicks++;
        } else {
            state.sleepTicks = 0;
        }

        state.omega = Math.max(0.0F, omega);
        state.zone = zoneFor(state, target, state.overloadLatched, previousZone, settings);

        if (state.sleepTicks >= settings.sleepTicks() && !state.stalled && !state.overloadLatched) {
            state.zone = Zone.SLEEPING;
        }

        float finalStress = k * state.omega;
        boolean changed = Math.abs(state.omega - previous) > EPSILON
                || wasLatched != state.overloadLatched
                || wasStalled != state.stalled
                || previousZone != state.zone;

        return new StepResult(
                state.omega,
                target,
                acceleration,
                finalStress,
                omegaEq,
                state.overloadLatched,
                state.stalled,
                state.zone,
                changed,
                state.sleepTicks
        );
    }

    private static Zone zoneFor(State state, float target, boolean latched, Zone previousZone,
                                DynamicsSettings settings) {
        if (state.stalled) {
            return Zone.STALLED;
        }
        if (latched) {
            return Zone.OVERLOADED;
        }
        if (state.sleepTicks >= settings.sleepTicks()) {
            return Zone.SLEEPING;
        }
        if (state.omega < target - TARGET_EPSILON) {
            return Zone.ACCELERATING;
        }
        if (state.omega > target + TARGET_EPSILON) {
            return Zone.DECELERATING;
        }
        return Zone.STEADY;
    }

    private static void updateSleep(State state, float target, DynamicsSettings settings) {
        if (Math.abs(target - state.omega) <= settings.sleepEpsilon()) {
            state.sleepTicks++;
            if (state.sleepTicks >= settings.sleepTicks() && !state.stalled) {
                state.zone = Zone.SLEEPING;
            }
        } else {
            state.sleepTicks = 0;
        }
    }

    private static StepResult result(State state, float target, float acceleration,
                                     float stress, float omegaEq, boolean latched, boolean stalled,
                                     boolean changed) {
        return new StepResult(state.omega, target, acceleration, stress, omegaEq, latched, stalled,
                state.zone, changed, state.sleepTicks);
    }

    private static float approach(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.copySign(maxDelta, target - current);
    }
}
