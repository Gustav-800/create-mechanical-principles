package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import dev.cmp.components.TransmissionControllers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpeedControllerBlockEntity.class)
public abstract class SpeedControllerBlockEntityMixin {
    @Inject(method = "getConveyedSpeed", at = @At("RETURN"), cancellable = true)
    private static void cmp$throttleOutput(KineticBlockEntity cogWheel, KineticBlockEntity controller,
                                           boolean targetingController,
                                           CallbackInfoReturnable<Float> cir) {
        if (controller instanceof SpeedControllerBlockEntity speedController
                && controller.getLevel() != null
                && !controller.getLevel().isClientSide()) {
            cir.setReturnValue(TransmissionControllers.speedControllerOutput(
                    speedController, cir.getReturnValue()));
        }
    }
}
