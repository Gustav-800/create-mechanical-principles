package dev.cmp.components;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.ComponentSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Gearshift transition state machine.
 *
 * <p>The output side is held at zero for a configurable number of ticks during
 * both the forward-to-reverse and reverse-to-forward transitions. Only then is
 * Create allowed to propagate the new direction. This guarantees the
 * "stop before reversing" ordering without fighting Create's rigid network
 * representation.</p>
 */
public final class GearshiftController {
    private final GearshiftBlockEntity gearshift;
    private TransmissionPhase phase;
    private Boolean lastPowered;
    private int countdown;

    public GearshiftController(GearshiftBlockEntity gearshift) {
        this.gearshift = gearshift;
    }

    public void tick() {
        if (gearshift.getLevel() == null || gearshift.getLevel().isClientSide()) {
            return;
        }
        boolean powered = gearshift.getBlockState().getValue(BlockStateProperties.POWERED);
        ensureState(powered);
        if (isTransitioning()) {
            if (countdown > 0) {
                countdown--;
            }
            if (countdown <= 0) {
                phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
                RotationPropagator.handleAdded(gearshift.getLevel(), gearshift.getBlockPos(), gearshift);
            }
        }
    }

    public float adjustModifier(Direction face, float original) {
        if (!gearshift.hasSource() || face == gearshift.getSourceFacing()) {
            return original;
        }
        boolean powered = gearshift.getBlockState().getValue(BlockStateProperties.POWERED);
        ensureState(powered);
        if (isTransitioning()) {
            return 0.0F;
        }
        return original;
    }

    private void ensureState(boolean powered) {
        ComponentSettings settings = CMPConfig.components();
        if (lastPowered == null) {
            lastPowered = powered;
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            return;
        }
        if (powered == lastPowered) {
            return;
        }
        lastPowered = powered;

        if (settings.refuseReverseWithForeignSource() && hasForeignSourceOnOutput()) {
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            countdown = 0;
            return;
        }

        int ticks = Math.max(1, Math.round(settings.reverseTransitionTicks() / settings.reverseAccelMultiplier()));
        phase = powered ? TransmissionPhase.REVERSING : TransmissionPhase.RETURNING;
        countdown = Math.max(0, ticks);
    }

    private boolean hasForeignSourceOnOutput() {
        if (gearshift.getLevel() == null || !gearshift.hasSource()) {
            return false;
        }
        Direction input = gearshift.getSourceFacing();
        for (Direction direction : Direction.values()) {
            if (direction == input) {
                continue;
            }
            BlockPos neighbourPos = gearshift.getBlockPos().relative(direction);
            if (!gearshift.getLevel().isLoaded(neighbourPos)) {
                continue;
            }
            if (gearshift.getLevel().getBlockEntity(neighbourPos) instanceof KineticBlockEntity neighbour
                    && RotationPropagator.isConnected(gearshift, neighbour)
                    && neighbour.hasSource()
                    && !neighbour.source.equals(gearshift.getBlockPos())
                    && Math.abs(neighbour.getSpeed()) > 1.0E-4F) {
                return true;
            }
        }
        return false;
    }

    public boolean isTransitioning() {
        return phase == TransmissionPhase.REVERSING || phase == TransmissionPhase.RETURNING;
    }

    public TransmissionPhase phase() {
        return phase == null ? TransmissionPhase.IDLE : phase;
    }

    public int countdown() {
        return countdown;
    }
}
