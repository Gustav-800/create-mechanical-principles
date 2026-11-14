package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.dynamics.SoftNetworkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cheap topology/capacity version used by the per-network cache. The counter
 * changes only when membership or member stress/capacity change, never when
 * the manager merely writes a display speed.
 */
@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin implements SoftNetworkAccess {
    @Unique
    private int cmp$topologyVersion;

    @Override
    public int cmp$getTopologyVersion() {
        return cmp$topologyVersion;
    }

    @Override
    public void cmp$bumpTopologyVersion() {
        cmp$topologyVersion++;
    }

    @Inject(method = "initFromTE", at = @At("TAIL"))
    private void cmp$bumpInit(float maxStress, float currentStress, int members, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "addSilently", at = @At("TAIL"))
    private void cmp$bumpAddSilently(KineticBlockEntity be, float lastCapacity, float lastStress, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void cmp$bumpAdd(KineticBlockEntity be, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void cmp$bumpRemove(KineticBlockEntity be, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "updateCapacityFor", at = @At("TAIL"))
    private void cmp$bumpCapacity(KineticBlockEntity be, float capacity, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "updateStressFor", at = @At("TAIL"))
    private void cmp$bumpStress(KineticBlockEntity be, float stress, CallbackInfo ci) {
        cmp$topologyVersion++;
    }

    @Inject(method = "updateNetwork", at = @At("TAIL"))
    private void cmp$bumpNetwork(CallbackInfo ci) {
        cmp$topologyVersion++;
    }
}
