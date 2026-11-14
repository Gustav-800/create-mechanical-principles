package dev.cmp.hotplug;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.ComponentSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admission control for placing a kinetic component next to an already running
 * network.
 *
 * <p>Create's propagation is intentionally not forked. The placement event
 * records the position; the actual decision is made before Create attaches the
 * new block entity (and also on the next server tick if the block entity was
 * not yet available). Rejection destroys the newly placed block with drops,
 * matching the task book's "break the connecting component" rule.</p>
 */
public final class HotPlugGuard {
    private static final Map<Level, List<PendingPlacement>> PENDING = new HashMap<>();

    private HotPlugGuard() {
    }

    public static void schedule(Level level, BlockPos pos, Entity placer) {
        if (level.isClientSide()) {
            return;
        }
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingPlacement(pos.immutable(), level.getGameTime(), placer));
        // In most cases the block entity already exists; resolve immediately so
        // Create never sees an invalid attachment. If it does not exist yet, the
        // pending entry is consumed by beforeAttach or processPending.
        evaluate(level, pos, placer);
    }

    /**
     * Called from the RotationPropagator mixin before Create attaches a block
     * entity. Returns false when the attachment must be cancelled.
     */
    public static boolean beforeAttach(Level level, BlockPos pos, KineticBlockEntity be) {
        List<PendingPlacement> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return true;
        }
        for (Iterator<PendingPlacement> iterator = pending.iterator(); iterator.hasNext(); ) {
            PendingPlacement placement = iterator.next();
            if (!placement.pos.equals(pos)) {
                continue;
            }
            iterator.remove();
            return evaluate(level, pos, placement.placer);
        }
        return true;
    }

    public static void processPending(MinecraftServer server) {
        for (Map.Entry<Level, List<PendingPlacement>> entry : new ArrayList<>(PENDING.entrySet())) {
            Level level = entry.getKey();
            List<PendingPlacement> list = entry.getValue();
            for (Iterator<PendingPlacement> iterator = list.iterator(); iterator.hasNext(); ) {
                PendingPlacement placement = iterator.next();
                if (level.getGameTime() >= placement.gameTime) {
                    iterator.remove();
                    evaluate(level, placement.pos, placement.placer);
                }
            }
            if (list.isEmpty()) {
                PENDING.remove(level);
            }
        }
    }

    private static boolean evaluate(Level level, BlockPos pos, Entity placer) {
        if (level.isClientSide() || !level.isLoaded(pos)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IRotate)) {
            return true;
        }
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity be)) {
            return true;
        }

        ComponentSettings settings = CMPConfig.components();
        Set<KineticBlockEntity> neighbours = connectedNeighbours(level, pos, be, state);
        Map<Long, Float> speeds = new HashMap<>();
        float maxSpeed = 0.0F;
        for (KineticBlockEntity neighbour : neighbours) {
            if (!neighbour.hasNetwork() || neighbour.network == null) {
                continue;
            }
            float speed = neighbour.getSpeed();
            if (Math.abs(speed) < 1.0E-4F) {
                continue;
            }
            speeds.merge(neighbour.network, speed, (first, second) ->
                    Math.abs(first) >= Math.abs(second) ? first : second);
            if (Math.abs(speed) > Math.abs(maxSpeed)) {
                maxSpeed = speed;
            }
        }

        if (speeds.size() >= 2) {
            float first = 0.0F;
            boolean firstSet = false;
            for (float speed : speeds.values()) {
                if (!firstSet) {
                    first = speed;
                    firstSet = true;
                } else if (Math.signum(first) == Math.signum(speed)
                        && Math.abs(first - speed) > settings.syncEpsilon()) {
                    return reject(level, pos, be, placer, "cmp.hotplug.conflict");
                }
            }
        }

        float limit = Math.abs(maxSpeed);
        if (settings.hotPlugDisabled()) {
            if (limit > 0.0F) {
                return reject(level, pos, be, placer, "cmp.hotplug.disabled");
            }
        } else if (!settings.hotPlugUnlimited() && limit > settings.hotPlugSpeedThreshold()) {
            return reject(level, pos, be, placer, "cmp.hotplug.too_fast");
        }
        return true;
    }

    private static Set<KineticBlockEntity> connectedNeighbours(Level level, BlockPos pos,
                                                               KineticBlockEntity be, BlockState state) {
        Set<BlockPos> candidates = new HashSet<>();
        for (Direction direction : Direction.values()) {
            candidates.add(pos.relative(direction));
        }
        if (state.getBlock() instanceof IRotate rotate) {
            List<BlockPos> propagation = new ArrayList<>();
            be.addPropagationLocations(rotate, state, propagation);
            candidates.addAll(propagation);
        }

        Set<KineticBlockEntity> neighbours = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof KineticBlockEntity other
                    && other != be
                    && (RotationPropagator.isConnected(be, other) || RotationPropagator.isConnected(other, be))) {
                neighbours.add(other);
            }
        }
        return neighbours;
    }

    private static boolean reject(Level level, BlockPos pos, KineticBlockEntity be,
                                  Entity placer, String messageKey) {
        if (!level.isLoaded(pos)) {
            return true;
        }
        level.destroyBlock(pos, true);
        if (placer instanceof Player player) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        dev.cmp.CreateMechanicalPrinciples.LOGGER.debug(
                "Rejected hot-plug of {} at {}: {}", be.getBlockState().getBlock(), pos, messageKey);
        return false;
    }

    public static void clear(Level level) {
        PENDING.remove(level);
    }

    public static void clear() {
        PENDING.clear();
    }

    private record PendingPlacement(BlockPos pos, long gameTime, Entity placer) {
    }
}
