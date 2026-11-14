package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.cmp.components.TransmissionControllers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SequencedGearshiftBlockEntity.class)
public abstract class SequencedGearshiftBlockEntityMixin {
    @Inject(method = "run", at = @At("TAIL"))
    private void cmp$onRun(int instructionIndex, CallbackInfo ci) {
        TransmissionControllers.sequencer((SequencedGearshiftBlockEntity) (Object) this)
                .onRun(instructionIndex);
    }

    @Inject(method = "getModifier", at = @At("RETURN"), cancellable = true)
    private void cmp$startupModifier(CallbackInfoReturnable<Integer> cir) {
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) {
            return;
        }
        cir.setReturnValue(TransmissionControllers.sequencer(self).adjustModifier(cir.getReturnValue()));
    }
}
