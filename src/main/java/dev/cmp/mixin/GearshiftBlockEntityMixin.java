package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity;
import dev.cmp.components.TransmissionControllers;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GearshiftBlockEntity.class)
public abstract class GearshiftBlockEntityMixin {
    @Inject(method = "getRotationSpeedModifier", at = @At("RETURN"), cancellable = true)
    private void cmp$softGearshift(Direction face, CallbackInfoReturnable<Float> cir) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) {
            return;
        }
        cir.setReturnValue(TransmissionControllers.gearshiftModifier(
                (GearshiftBlockEntity) (Object) this, face, cir.getReturnValue()));
    }
}
