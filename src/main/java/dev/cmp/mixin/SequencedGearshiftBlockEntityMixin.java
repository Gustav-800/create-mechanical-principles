package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.cmp.components.TransmissionControllers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts the output-side ramp after Create has selected the new step ratio.
 * The modifier itself is never overridden; overriding it prevented the output
 * network from being reattached and made the gearbox unable to transmit power.
 */
@Mixin(SequencedGearshiftBlockEntity.class)
public abstract class SequencedGearshiftBlockEntityMixin {
    @Inject(method = "run", at = @At("TAIL"))
    private void cmp$onRun(int instructionIndex, CallbackInfo ci) {
        TransmissionControllers.sequencer((SequencedGearshiftBlockEntity) (Object) this)
                .onRun(instructionIndex);
    }
}
