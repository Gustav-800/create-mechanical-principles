package dev.cmp.components;

import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.cmp.config.CMPConfig;

/**
 * Sequencer startup gate. Create's sequencer changes its output modifier at
 * the beginning of a step; holding the output at zero for a short, configurable
 * time makes the step start from rest instead of applying a step impulse.
 *
 * <p>The trapezoidal/triangular distance planner lives in {@link SequencerPlan}
 * and is covered by unit tests; this controller is the conservative first
 * version that does not replace Create's own instruction timer.</p>
 */
public final class SequencerController {
    private final SequencedGearshiftBlockEntity sequencer;
    private TransmissionPhase phase = TransmissionPhase.IDLE;
    private int startup;

    public SequencerController(SequencedGearshiftBlockEntity sequencer) {
        this.sequencer = sequencer;
    }

    public void onRun(int instructionIndex) {
        if (instructionIndex >= 0) {
            phase = TransmissionPhase.STARTING;
            startup = CMPConfig.components().sequencerStartupTicks();
        } else {
            phase = TransmissionPhase.IDLE;
            startup = 0;
        }
    }

    public void tick() {
        if (sequencer.getLevel() == null || sequencer.getLevel().isClientSide()) {
            return;
        }
        if (phase == TransmissionPhase.STARTING) {
            if (startup > 0) {
                startup--;
            }
            if (startup <= 0) {
                phase = TransmissionPhase.RUNNING;
            }
        }
    }

    public int adjustModifier(int original) {
        if (CMPConfig.components().sequencerStartupTicks() <= 0) {
            return original;
        }
        return phase == TransmissionPhase.STARTING ? 0 : original;
    }

    public TransmissionPhase phase() {
        return phase;
    }
}
