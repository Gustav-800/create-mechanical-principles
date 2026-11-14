package dev.cmp.components;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.dynamics.SoftNetworkManager;
import dev.cmp.dynamics.SoftSpeedAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable output-side speed ramp for split-shaft transmissions.
 *
 * <p>The input side is never touched. After Create attaches the output at the
 * new ratio, the members reachable from the output face are captured as speed
 * targets, set to zero, and multiplied by a 0..1 factor each tick. The global
 * network solver is suspended for the duration so it cannot overwrite the
 * deliberate two-sided profile.</p>
 */
public final class SoftOutputRamp {
    private final KineticBlockEntity source;
    private final Direction inputFace;
    private final float accelerationFraction;
    private final List<Target> targets = new ArrayList<>();

    private boolean active;
    private float factor;
    private float initialSourceSpeed;
    private long networkId = -1L;

    public SoftOutputRamp(KineticBlockEntity source, Direction inputFace, float accelerationFraction) {
        this.source = source;
        this.inputFace = inputFace;
        this.accelerationFraction = Math.max(0.01F, Math.min(1.0F, accelerationFraction));
    }

    /**
     * Attach and capture the output targets. The caller must have arranged for
     * the source's rotation modifier to report the new ratio during this call.
     */
    public boolean begin() {
        Level level = source.getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        active = true;
        factor = 0.0F;
        RotationPropagator.handleAdded(level, source.getBlockPos(), source);
        if (source.network != null) {
            networkId = source.network;
            SoftNetworkManager.suspend(level, networkId, 400);
        }
        collect();
        initialSourceSpeed = Math.abs(source.getTheoreticalSpeed());
        if (targets.isEmpty() || initialSourceSpeed <= 1.0E-4F) {
            complete();
            return false;
        }
        apply();
        return true;
    }

    public void step() {
        if (!active) {
            return;
        }
        Level level = source.getLevel();
        if (level == null || level.isClientSide() || source.isRemoved()) {
            cancel();
            return;
        }
        factor = Math.min(1.0F, factor + accelerationFraction);
        apply();
        if (factor >= 1.0F) {
            complete();
        }
    }

    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        targets.clear();
        resume();
    }

    public boolean isActive() {
        return active;
    }

    public float factor() {
        return factor;
    }

    private void apply() {
        Level level = source.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        float sourceSpeed = Math.abs(source.getTheoreticalSpeed());
        if (initialSourceSpeed > 1.0E-4F && sourceSpeed > 1.0E-4F
                && Math.abs(sourceSpeed - initialSourceSpeed) > 1.0E-4F) {
            float scale = sourceSpeed / initialSourceSpeed;
            for (Target target : targets) {
                target.speed *= scale;
            }
            initialSourceSpeed = sourceSpeed;
        }

        for (Target target : targets) {
            if (!target.present()) {
                continue;
            }
            target.access().cmp$applyDisplaySpeed(target.speed * factor);
            target.be.sendData();
        }
        KineticNetwork network = source.getOrCreateNetwork();
        if (network != null && network.initialized) {
            network.updateStress();
        }
    }

    private void complete() {
        if (!active) {
            return;
        }
        for (Target target : targets) {
            if (target.present()) {
                target.access().cmp$applyDisplaySpeed(target.speed);
                target.be.sendData();
            }
        }
        targets.clear();
        active = false;
        resume();
        KineticNetwork network = source.getOrCreateNetwork();
        if (network != null) {
            network.updateNetwork();
        }
    }

    private void resume() {
        if (networkId >= 0L && source.getLevel() != null) {
            SoftNetworkManager.resume(source.getLevel(), networkId);
            networkId = -1L;
        }
    }

    private void collect() {
        targets.clear();
        Level level = source.getLevel();
        if (level == null || inputFace == null) {
            return;
        }
        Set<BlockPos> inputNeighbours = new HashSet<>();
        inputNeighbours.add(source.getBlockPos().relative(inputFace));

        Set<KineticBlockEntity> visited = new HashSet<>();
        ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            if (direction == inputFace) {
                continue;
            }
            BlockPos pos = source.getBlockPos().relative(direction);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof KineticBlockEntity neighbour
                    && outputConnection(neighbour)
                    && visited.add(neighbour)) {
                queue.add(neighbour);
            }
        }

        while (!queue.isEmpty()) {
            KineticBlockEntity current = queue.poll();
            if (current == null || current == source) {
                continue;
            }
            targets.add(new Target(current, current.getTheoreticalSpeed()));
            for (BlockPos candidate : neighboursOf(current)) {
                if (inputNeighbours.contains(candidate) || !level.isLoaded(candidate)) {
                    continue;
                }
                if (level.getBlockEntity(candidate) instanceof KineticBlockEntity next
                        && next != source
                        && visited.add(next)
                        && outputConnection(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private boolean outputConnection(KineticBlockEntity candidate) {
        if (candidate.getLevel() != source.getLevel()) {
            return false;
        }
        if (source.network != null && candidate.network != null
                && !source.network.equals(candidate.network)) {
            return false;
        }
        return RotationPropagator.isConnected(source, candidate)
                || RotationPropagator.isConnected(candidate, source);
    }

    private List<BlockPos> neighboursOf(KineticBlockEntity be) {
        List<BlockPos> positions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            positions.add(be.getBlockPos().relative(direction));
        }
        if (be.getBlockState().getBlock() instanceof IRotate rotate) {
            be.addPropagationLocations(rotate, be.getBlockState(), positions);
        }
        return positions;
    }

    private static final class Target {
        private final KineticBlockEntity be;
        private float speed;

        private Target(KineticBlockEntity be, float speed) {
            this.be = be;
            this.speed = speed;
        }

        private boolean present() {
            Level level = be.getLevel();
            return level != null && level.getBlockEntity(be.getBlockPos()) == be;
        }

        private SoftSpeedAccess access() {
            return (SoftSpeedAccess) be;
        }
    }
}
