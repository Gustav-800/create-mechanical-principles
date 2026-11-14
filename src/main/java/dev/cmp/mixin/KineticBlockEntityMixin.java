package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.dynamics.SoftNetworkManager;
import dev.cmp.dynamics.SoftSpeedAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries the unthrottled nominal speed alongside Create's own speed field.
 *
 * <p>Create remains the owner of topology and stress aggregation. The manager
 * writes only the display speed, so all existing Create consumers (gauges,
 * contraptions, renderers) keep reading the same field. The nominal value is
 * persisted so a reload resumes at the current soft speed and can still ramp
 * back to the original source speed.</p>
 */
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin implements SoftSpeedAccess {
    @Shadow
    protected float speed;

    @Shadow
    protected boolean overStressed;

    @Unique
    private float cmp$nominalSpeed = Float.NaN;

    @Unique
    private boolean cmp$applyingSoftSpeed;

    @Override
    public float cmp$getNominalSpeed() {
        if (!Float.isFinite(cmp$nominalSpeed)) {
            cmp$nominalSpeed = speed;
        }
        return cmp$nominalSpeed;
    }

    @Override
    public void cmp$setNominalSpeed(float nominalSpeed) {
        if (Float.isFinite(nominalSpeed)) {
            cmp$nominalSpeed = nominalSpeed;
        }
    }

    @Override
    public float cmp$getStoredSpeed() {
        return speed;
    }

    @Override
    public void cmp$applyDisplaySpeed(float displaySpeed) {
        cmp$applyingSoftSpeed = true;
        try {
            speed = Float.isFinite(displaySpeed) ? displaySpeed : 0.0F;
        } finally {
            cmp$applyingSoftSpeed = false;
        }
    }

    @Override
    public boolean cmp$isApplyingSoftSpeed() {
        return cmp$applyingSoftSpeed;
    }

    @Inject(method = "setSpeed", at = @At("HEAD"))
    private void cmp$captureNominalSpeed(float newSpeed, CallbackInfo ci) {
        if (!cmp$applyingSoftSpeed) {
            cmp$nominalSpeed = newSpeed;
            KineticBlockEntity self = (KineticBlockEntity) (Object) this;
            if (self.getLevel() != null && !self.getLevel().isClientSide()) {
                SoftNetworkManager.markDirty(self);
            }
        }
    }

    /**
     * Create normally returns zero RPM while overstressed. Soft dynamics keeps
     * the (already scaled) speed visible instead; the overstressed flag remains
     * set so meters and tooltips still communicate the overload.
     */
    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void cmp$keepSoftSpeedVisible(CallbackInfoReturnable<Float> cir) {
        if (!overStressed) {
            return;
        }
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (!SoftNetworkManager.isManaged(self)) {
            return;
        }
        Level level = self.getLevel();
        if (level != null && level.tickRateManager().isFrozen()) {
            cir.setReturnValue(0.0F);
            return;
        }
        cir.setReturnValue(self.getTheoreticalSpeed());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cmp$softTick(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        dev.cmp.components.TransmissionControllers.tick(self);
        SoftNetworkManager.onKineticTick(self);
    }

    /**
     * A stalled soft network legitimately has a zero-RPM source for a while.
     * Create's periodic source validation would otherwise tear the network
     * apart before the load drops and the solver can recover.
     */
    @Inject(method = "validateKinetics", at = @At("HEAD"), cancellable = true)
    private void cmp$protectSoftNetwork(CallbackInfo ci) {
        if (SoftNetworkManager.shouldProtect((KineticBlockEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void cmp$writeSoftSpeed(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                     CallbackInfo ci) {
        float nominal = cmp$getNominalSpeed();
        tag.putFloat("SoftDynamicsSpeed", speed);
        tag.putFloat("SoftDynamicsNominalSpeed", nominal);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void cmp$readSoftSpeed(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        if (tag.contains("SoftDynamicsNominalSpeed")) {
            cmp$nominalSpeed = tag.getFloat("SoftDynamicsNominalSpeed");
        } else {
            // First load after adding the mod: current Create speed is the best
            // available nominal value.
            cmp$nominalSpeed = speed;
        }
    }

    @Inject(method = "clearKineticInformation", at = @At("TAIL"))
    private void cmp$clearNominal(CallbackInfo ci) {
        cmp$nominalSpeed = 0.0F;
    }

    @Inject(method = "removeSource", at = @At("TAIL"))
    private void cmp$clearSourceNominal(CallbackInfo ci) {
        cmp$nominalSpeed = 0.0F;
    }
}
