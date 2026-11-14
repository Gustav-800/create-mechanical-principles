package dev.cmp.components;

import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.cmp.config.CMPConfig;

/**
 * Optional output ramp for a sequenced gearshift step.
 *
 * <p>The previous first-version gate returned zero from {@code getModifier} and
 * prevented Create from ever reattaching the output, which broke power
 * transmission. This controller instead lets Create attach the new ratio, then
 * ramps the members reachable from the output face from zero. It does not
 * replace Create's instruction timer; when a step is too short the next step
 * simply cancels and starts a fresh ramp.</p>
 */
public final class SequencerController {
    private final SequencedGearshiftBlockEntity sequencer;
    private SoftOutputRamp ramp;

    public SequencerController(SequencedGearshiftBlockEntity sequencer) {
        this.sequencer = sequencer;
    }

    public void onRun(int instructionIndex) {
        cancel();
        if (instructionIndex < 0 || !sequencer.hasSource()) {
            return;
        }
        int modifier = sequencer.getModifier();
        if (modifier == 0) {
            return;
        }
        float target = Math.abs(sequencer.getTheoreticalSpeed() * modifier);
        if (target <= 1.0E-4F) {
            return;
        }
        float fraction = CMPConfig.components().sequencerAcceleration() / Math.max(0.5F, target);
        fraction = Math.max(0.05F, Math.min(0.75F, fraction));
        ramp = new SoftOutputRamp(sequencer, sequencer.getSourceFacing(), fraction);
        if (!ramp.begin()) {
            ramp = null;
        }
    }

    public void tick() {
        if (ramp == null) {
            return;
        }
        ramp.step();
        if (!ramp.isActive()) {
            ramp = null;
        }
    }

    public void cancel() {
        if (ramp != null) {
            ramp.cancel();
            ramp = null;
        }
    }
}
