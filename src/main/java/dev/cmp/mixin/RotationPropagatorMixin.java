package dev.cmp.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.dynamics.SoftNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notifies the soft manager around Create's rigid-network propagation. The
 * actual hot-plug admission check runs from the NeoForge placement event, which
 * has reliable placement context. This hook only invalidates cached K/C when a
 * network is attached or detached.
 */
@Mixin(RotationPropagator.class)
public abstract class RotationPropagatorMixin {
    @Inject(method = "handleAdded", at = @At("HEAD"), cancellable = true)
    private static void cmp$onAdded(Level world, BlockPos pos, KineticBlockEntity added, CallbackInfo ci) {
        if (!world.isClientSide) {
            if (!dev.cmp.hotplug.HotPlugGuard.beforeAttach(world, pos, added)) {
                ci.cancel();
                return;
            }
            SoftNetworkManager.topologyChanged(world, added);
        }
    }

    @Inject(method = "handleRemoved", at = @At("HEAD"))
    private static void cmp$onRemoved(Level world, BlockPos pos, KineticBlockEntity removed, CallbackInfo ci) {
        if (!world.isClientSide) {
            SoftNetworkManager.topologyChanged(world, removed);
        }
    }
}
