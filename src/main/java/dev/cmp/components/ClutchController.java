package dev.cmp.components;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.ComponentSettings;
import dev.cmp.dynamics.SoftNetworkManager;
import dev.cmp.dynamics.SoftSpeedAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clutch soft-start state machine.
 *
 * <p>After the clutch is released, the output side is attached, then its
 * existing speed targets are scaled down and ramped back to full over several
 * ticks. The input side is never changed. Create still owns the network, so the
 * only thing this controller writes is a temporary display speed on the members
 * reachable from the output face; nominal speeds stay untouched.</p>
 */
public final class ClutchController {
    private final ClutchBlockEntity clutch;
    private TransmissionPhase phase = TransmissionPhase.IDLE;
    private Boolean lastPowered;
    private int countdown;
    private float rampFactor;
    private float initialSourceSpeed;
    private long networkId = -1L;
    private final List<DownstreamTarget> downstream = new ArrayList<>();

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
            if (phase == TransmissionPhase.SOFT_START || phase == TransmissionPhase.RAMPING) {
                cancelRamp();
            }
            phase = TransmissionPhase.DISENGAGED;
            countdown = 0;
            lastPowered = true;
            return;
        }

        if (lastPowered && phase == TransmissionPhase.DISENGAGED) {
            if (settings.clutchSoftStart()) {
                phase = TransmissionPhase.SOFT_START;
                countdown = Math.max(0, settings.clutchEngageDelayTicks());
                if (countdown == 0) {
                    beginRamp();
                }
            } else {
                phase = TransmissionPhase.SYNCED;
            }
        }

        if (phase == TransmissionPhase.SOFT_START) {
            if (countdown > 0) {
                countdown--;
            }
            if (countdown <= 0) {
                beginRamp();
            }
        } else if (phase == TransmissionPhase.RAMPING) {
            stepRamp();
        }
        lastPowered = false;
    }

    /**
     * Called from the clutch's getRotationSpeedModifier mixin. During the
     * configured delay and while disconnected the output reports zero; once
     * the ramp starts, Create sees the normal ratio and the controller writes
     * the temporary output speed directly.
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
        } else if (!powered && lastPowered && phase == TransmissionPhase.DISENGAGED) {
            phase = TransmissionPhase.SOFT_START;
            countdown = Math.max(0, settings.clutchEngageDelayTicks());
        } else if (powered) {
            phase = TransmissionPhase.DISENGAGED;
        }

        if (phase == TransmissionPhase.SOFT_START) {
            return 0.0F;
        }
        return original;
    }

    private void beginRamp() {
        phase = TransmissionPhase.RAMPING;
        networkId = clutch.network == null ? -1L : clutch.network;
        // Attach at full speed first; the current per-member speeds become the
        // ramp targets. We immediately scale those members to zero below.
        RotationPropagator.handleAdded(clutch.getLevel(), clutch.getBlockPos(), clutch);
        if (clutch.network != null) {
            networkId = clutch.network;
            SoftNetworkManager.suspend(clutch.getLevel(), networkId, 600);
        }
        collectDownstream();
        initialSourceSpeed = Math.abs(clutch.getTheoreticalSpeed());
        rampFactor = 0.0F;
        if (downstream.isEmpty() || initialSourceSpeed <= 1.0E-4F) {
            completeRamp();
            return;
        }
        applyRamp();
    }

    private void stepRamp() {
        if (clutch.getLevel() == null || clutch.getLevel().isClientSide()) {
            return;
        }
        if (clutch.getBlockState().getValue(BlockStateProperties.POWERED)) {
            cancelRamp();
            return;
        }
        ComponentSettings settings = CMPConfig.components();
        rampFactor = Math.min(1.0F, rampFactor + settings.clutchAccelFraction());
        applyRamp();
        if (rampFactor >= 1.0F) {
            completeRamp();
        }
    }

    private void applyRamp() {
        Level level = clutch.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        // If the input source speed changed while ramping, keep the output
        // targets proportional to it.
        float source = Math.abs(clutch.getTheoreticalSpeed());
        if (initialSourceSpeed > 1.0E-4F && source > 1.0E-4F
                && Math.abs(source - initialSourceSpeed) > 1.0E-4F) {
            float scale = source / initialSourceSpeed;
            for (DownstreamTarget target : downstream) {
                target.targetSpeed *= scale;
            }
            initialSourceSpeed = source;
        }

        for (DownstreamTarget target : downstream) {
            if (!target.isPresent()) {
                continue;
            }
            SoftSpeedAccess access = target.access();
            access.cmp$applyDisplaySpeed(target.targetSpeed * rampFactor);
            target.be.sendData();
        }
        if (networkId >= 0L) {
            com.simibubi.create.content.kinetics.KineticNetwork network =
                    clutch.getOrCreateNetwork();
            if (network != null && network.initialized) {
                network.updateStress();
            }
        }
    }

    private void completeRamp() {
        for (DownstreamTarget target : downstream) {
            if (target.isPresent()) {
                target.access().cmp$applyDisplaySpeed(target.targetSpeed);
                target.be.sendData();
            }
        }
        downstream.clear();
        phase = TransmissionPhase.SYNCED;
        resumeNetwork();
        if (clutch.getLevel() != null) {
            com.simibubi.create.content.kinetics.KineticNetwork network = clutch.getOrCreateNetwork();
            if (network != null) {
                network.updateNetwork();
            }
        }
    }

    private void cancelRamp() {
        downstream.clear();
        phase = TransmissionPhase.DISENGAGED;
        resumeNetwork();
    }

    private void resumeNetwork() {
        if (networkId >= 0L && clutch.getLevel() != null) {
            SoftNetworkManager.resume(clutch.getLevel(), networkId);
            networkId = -1L;
        }
    }

    private void collectDownstream() {
        downstream.clear();
        Level level = clutch.getLevel();
        if (level == null || !clutch.hasSource()) {
            return;
        }
        Direction input = clutch.getSourceFacing();
        Set<BlockPos> inputNeighbours = new HashSet<>();
        for (Direction direction : Direction.values()) {
            if (direction == input) {
                inputNeighbours.add(clutch.getBlockPos().relative(direction));
            }
        }

        Set<KineticBlockEntity> visited = new HashSet<>();
        ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            if (direction == input) {
                continue;
            }
            BlockPos pos = clutch.getBlockPos().relative(direction);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof KineticBlockEntity neighbour
                    && isOutputConnection(neighbour)
                    && visited.add(neighbour)) {
                queue.add(neighbour);
            }
        }

        while (!queue.isEmpty()) {
            KineticBlockEntity current = queue.poll();
            if (current == null || current == clutch) {
                continue;
            }
            DownstreamTarget target = new DownstreamTarget(current, current.getTheoreticalSpeed());
            downstream.add(target);
            for (BlockPos candidate : candidateNeighbours(current)) {
                if (inputNeighbours.contains(candidate) || !level.isLoaded(candidate)) {
                    continue;
                }
                if (level.getBlockEntity(candidate) instanceof KineticBlockEntity next
                        && next != clutch
                        && visited.add(next)
                        && isOutputConnection(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private boolean isOutputConnection(KineticBlockEntity candidate) {
        if (clutch.getLevel() == null || candidate.getLevel() != clutch.getLevel()) {
            return false;
        }
        if (clutch.network != null && candidate.network != null
                && !clutch.network.equals(candidate.network)) {
            return false;
        }
        return RotationPropagator.isConnected(clutch, candidate)
                || RotationPropagator.isConnected(candidate, clutch);
    }

    private List<BlockPos> candidateNeighbours(KineticBlockEntity be) {
        List<BlockPos> positions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            positions.add(be.getBlockPos().relative(direction));
        }
        if (be.getBlockState().getBlock() instanceof IRotate rotate) {
            be.addPropagationLocations(rotate, be.getBlockState(), positions);
        }
        return positions;
    }

    public TransmissionPhase phase() {
        return phase;
    }

    public int countdown() {
        return countdown;
    }

    public float rampFactor() {
        return rampFactor;
    }

    /**
     * A downstream member captured at the moment the clutch attached. The
     * target speed already includes every gear ratio; only a scalar ramp is
     * applied on top.
     */
    private final class DownstreamTarget {
        private final KineticBlockEntity be;
        private float targetSpeed;

        private DownstreamTarget(KineticBlockEntity be, float targetSpeed) {
            this.be = be;
            this.targetSpeed = targetSpeed;
        }

        private boolean isPresent() {
            Level level = be.getLevel();
            return level != null && level.getBlockEntity(be.getBlockPos()) == be;
        }

        private SoftSpeedAccess access() {
            return (SoftSpeedAccess) be;
        }
    }
}
