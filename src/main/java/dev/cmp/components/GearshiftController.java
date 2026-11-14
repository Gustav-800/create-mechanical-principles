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
 * <p>The output side is first held at zero for a short transition window, then
 * reattached in the new direction and ramped from zero to the new target speed.
 * Create's split-shaft block still owns direction selection; the ramp only
 * writes the temporary speed of the members reachable from the output face.</p>
 */
public final class GearshiftController {
    private final GearshiftBlockEntity gearshift;
    private TransmissionPhase phase = TransmissionPhase.IDLE;
    private Boolean lastPowered;
    private int countdown;
    private SoftOutputRamp ramp;

    public GearshiftController(GearshiftBlockEntity gearshift) {
        this.gearshift = gearshift;
    }

    public void tick() {
        if (gearshift.getLevel() == null || gearshift.getLevel().isClientSide()) {
            return;
        }
        boolean powered = gearshift.getBlockState().getValue(BlockStateProperties.POWERED);
        if (!gearshift.hasSource()) {
            cancelRamp();
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            lastPowered = powered;
            return;
        }
        ensureState(powered);

        if (phase == TransmissionPhase.REVERSING || phase == TransmissionPhase.RETURNING) {
            if (countdown > 0) {
                countdown--;
            }
            if (countdown <= 0) {
                beginRamp(powered);
            }
        } else if (phase == TransmissionPhase.RAMPING && ramp != null) {
            ramp.step();
            if (!ramp.isActive()) {
                ramp = null;
                phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            }
        }
        lastPowered = powered;
    }

    public float adjustModifier(Direction face, float original) {
        if (!gearshift.hasSource() || face == gearshift.getSourceFacing()) {
            return original;
        }
        boolean powered = gearshift.getBlockState().getValue(BlockStateProperties.POWERED);
        ensureState(powered);

        if (phase == TransmissionPhase.REVERSING || phase == TransmissionPhase.RETURNING) {
            return 0.0F;
        }
        // During RAMPING the new direction is intentionally visible to Create;
        // the temporary output speed is managed by the ramp.
        return original;
    }

    private void ensureState(boolean powered) {
        if (lastPowered == null) {
            lastPowered = powered;
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            return;
        }
        if (powered == lastPowered) {
            return;
        }
        lastPowered = powered;
        cancelRamp();

        ComponentSettings settings = CMPConfig.components();
        if (settings.refuseReverseWithForeignSource() && hasForeignSourceOnOutput()) {
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
            countdown = 0;
            return;
        }

        phase = powered ? TransmissionPhase.REVERSING : TransmissionPhase.RETURNING;
        int ticks = Math.max(0,
                Math.round(settings.reverseTransitionTicks() / settings.reverseAccelMultiplier()));
        countdown = ticks;
    }

    private void beginRamp(boolean powered) {
        phase = TransmissionPhase.RAMPING;
        ComponentSettings settings = CMPConfig.components();
        float fraction = Math.max(0.10F, Math.min(0.75F,
                settings.clutchAccelFraction() * settings.reverseAccelMultiplier()));
        ramp = new SoftOutputRamp(gearshift, gearshift.getSourceFacing(), fraction);
        if (!ramp.begin()) {
            ramp = null;
            phase = powered ? TransmissionPhase.REVERSED : TransmissionPhase.FORWARD;
        }
    }

    private void cancelRamp() {
        if (ramp != null) {
            ramp.cancel();
            ramp = null;
        }
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
        return phase == TransmissionPhase.REVERSING
                || phase == TransmissionPhase.RETURNING
                || phase == TransmissionPhase.RAMPING;
    }

    public TransmissionPhase phase() {
        return phase == null ? TransmissionPhase.IDLE : phase;
    }

    public int countdown() {
        return countdown;
    }
}
