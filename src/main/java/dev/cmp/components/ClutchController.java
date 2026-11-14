package dev.cmp.components;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.ComponentSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Clutch soft-start state machine.
 *
 * <p>Create's clutch is a single block entity with one speed field, so a true
 * two-sided slipping model would require a second network. This controller
 * instead implements the safe, observable first-version behaviour: after the
 * redstone signal is removed, the output side stays disengaged for
 * {@code clutchEngageDelayTicks}, then attaches at source speed. The network
 * solver supplies the inertia once connected. {@code autoMergeOnSync} is
 * satisfied by Create's normal single-network merge.</p>
 */
public final class ClutchController {
    private final ClutchBlockEntity clutch;
    private TransmissionPhase phase;
    private Boolean lastPowered;
    private int countdown;

    public ClutchController(ClutchBlockEntity clutch) {
        this.clutch = clutch;
    }

    public void tick() {
        if (clutch.getLevel() == null || clutch.getLevel().isClientSide()) {
            return;
        }
        ComponentSettings settings = CMPConfig.components();
        boolean powered = clutch.getBlockState().getValue(BlockStateProperties.POWERED);
        if (lastPowered == null) {
            lastPowered = powered;
            phase = powered ? TransmissionPhase.DISENGAGED : TransmissionPhase.SYNCED;
            return;
        }

        if (powered) {
            phase = TransmissionPhase.DISENGAGED;
            countdown = 0;
        } else if (lastPowered && phase != TransmissionPhase.SOFT_START) {
            if (settings.clutchSoftStart() && settings.clutchEngageDelayTicks() > 0) {
                phase = TransmissionPhase.SOFT_START;
                countdown = settings.clutchEngageDelayTicks();
            } else {
                phase = TransmissionPhase.SYNCED;
            }
        }

        if (phase == TransmissionPhase.SOFT_START) {
            if (countdown > 0) {
                countdown--;
            }
            if (countdown <= 0) {
                phase = TransmissionPhase.SYNCED;
                lastPowered = powered;
                RotationPropagator.handleAdded(clutch.getLevel(), clutch.getBlockPos(), clutch);
            }
        }
        lastPowered = powered;
    }

    /**
     * Called from the clutch's getRotationSpeedModifier mixin. The controller
     * also notices a fresh redstone release here, which avoids relying on the
     * block-entity tick order relative to Create's scheduled reattach tick.
     */
    public float adjustModifier(Direction face, float original) {
        if (!CMPConfig.components().clutchSoftStart() || !clutch.hasSource()) {
            return original;
        }
        if (face == clutch.getSourceFacing()) {
            return original;
        }
        ComponentSettings settings = CMPConfig.components();
        boolean powered = clutch.getBlockState().getValue(BlockStateProperties.POWERED);
        if (lastPowered == null) {
            lastPowered = powered;
            phase = powered ? TransmissionPhase.DISENGAGED : TransmissionPhase.SYNCED;
        } else if (!powered && lastPowered && (phase == TransmissionPhase.DISENGAGED || phase == null)) {
            if (settings.clutchEngageDelayTicks() > 0) {
                phase = TransmissionPhase.SOFT_START;
                countdown = settings.clutchEngageDelayTicks();
            }
        } else if (powered) {
            phase = TransmissionPhase.DISENGAGED;
        }

        if (phase == TransmissionPhase.SOFT_START) {
            return 0.0F;
        }
        return original;
    }

    public TransmissionPhase phase() {
        return phase == null ? TransmissionPhase.IDLE : phase;
    }

    public int countdown() {
        return countdown;
    }
}
