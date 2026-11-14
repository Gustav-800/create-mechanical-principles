package dev.cmp.dynamics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cmp.CreateMechanicalPrinciples;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.DynamicsSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side owner of one {@link NetworkState} per Create kinetic network.
 *
 * <p>The manager deliberately does not replace Create's torque propagation.
 * It observes a network, computes the aggregate C and K, and writes a scaled
 * display speed back into every member. Create's own stress aggregation then
 * sees the reduced speed and the network settles at omegaEq instead of
 * instantly stopping.</p>
 */
public final class SoftNetworkManager {
    private static final Map<Level, Map<Long, NetworkState>> STATES = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Long>> SUSPENDED = new WeakHashMap<>();
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private static int consecutiveErrors;
    private static long budgetGameTime = Long.MIN_VALUE;
    private static int budgetUsed;
    private static boolean wasActive;

    private SoftNetworkManager() {
    }

    public static void onKineticTick(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (be.isVirtual() || !be.hasNetwork() || be.network == null) {
            return;
        }

        Level level = be.getLevel();
        DynamicsSettings settings = CMPConfig.dynamics();
        if (!settings.enabled()) {
            if (wasActive) {
                restoreAll();
                wasActive = false;
            }
            return;
        }
        wasActive = true;

        try {
            process(level, be, settings);
            consecutiveErrors = 0;
        } catch (Throwable throwable) {
            consecutiveErrors++;
            CreateMechanicalPrinciples.LOGGER.error(
                    "Soft dynamics failed for network {} at {}; restoring nominal speeds (error {}/{})",
                    be.network, be.getBlockPos(), consecutiveErrors, MAX_CONSECUTIVE_ERRORS, throwable);
            try {
                restoreAll();
            } catch (Throwable restoreFailure) {
                CreateMechanicalPrinciples.LOGGER.error("Failed to restore nominal speeds after a soft-dynamics error",
                        restoreFailure);
            }
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS && CMPConfig.AUTO_SAFE_MODE.get()) {
                CMPConfig.enterSafeMode();
                wasActive = false;
            }
        }
    }

    private static void process(Level level, KineticBlockEntity be, DynamicsSettings settings) {
        KineticNetwork network = be.getOrCreateNetwork();
        if (network == null || !network.initialized || network.id == null) {
            return;
        }
        if (isSuspended(level, network.id)) {
            return;
        }
        if (network.members.isEmpty()) {
            removeState(level, network.id);
            return;
        }

        long gameTime = level.getGameTime();
        if (budgetGameTime != gameTime) {
            budgetGameTime = gameTime;
            budgetUsed = 0;
        }

        Map<Long, NetworkState> levelStates = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        NetworkState state = levelStates.computeIfAbsent(network.id, ignored -> new NetworkState());

        if (state.lastTick == gameTime) {
            return;
        }
        state.lastTick = gameTime;
        state.lastSeen = gameTime;

        int version = versionOf(network);
        boolean dirty = version != state.cachedVersion;
        if (dirty && state.lastRebuild != Long.MIN_VALUE
                && gameTime - state.lastRebuild < settings.topologyRebuildCooldown()) {
            dirty = false;
        }

        if (dirty || !state.hasCache) {
            if (!rebuildCache(state, network, settings)) {
                return;
            }
            state.cachedVersion = version;
            state.lastRebuild = gameTime;
            state.hasCache = true;
        }

        float omega0 = state.ratedSpeed;
        if (omega0 <= SoftDynamicsCore.EPSILON) {
            return;
        }

        if (!state.core.initialised()) {
            state.core.seed(seedOmega(network, omega0));
        }

        boolean sleeping = state.core.sleepTicks() >= settings.sleepTicks()
                && state.core.initialised()
                && !dirty;
        if (sleeping) {
            return;
        }

        // Only networks that are actually changing consume the per-tick budget.
        if (state.core.zone() != SoftDynamicsCore.Zone.SLEEPING && budgetUsed >= settings.maxComponentsPerTick()) {
            return;
        }
        budgetUsed++;

        SoftDynamicsCore.Inputs inputs = new SoftDynamicsCore.Inputs(
                state.capacity, state.stressCoefficient, omega0, true);
        SoftDynamicsCore.StepResult result = SoftDynamicsCore.step(state.core, inputs, settings);
        state.lastZone = result.zone();
        state.lastTargetOmega = result.targetOmega();

        float factor = result.omega() / omega0;
        factor = Math.max(0.0F, Math.min(1.0F, factor));
        boolean factorChanged = Math.abs(factor - state.appliedFactor) > 1.0E-6F;
        state.appliedFactor = factor;

        if (factorChanged) {
            applyFactor(network, state, factor, settings, gameTime);
            network.updateStress();
        }

        if (result.stalled() != state.wasStalled || result.overloadLatched() != state.wasLatched) {
            state.wasStalled = result.stalled();
            state.wasLatched = result.overloadLatched();
            state.lastSyncTick = Long.MIN_VALUE;
        }
    }

    private static boolean rebuildCache(NetworkState state, KineticNetwork network, DynamicsSettings settings) {
        state.network = network;
        state.members.clear();
        state.members.addAll(network.members.keySet());
        float omega0 = 0.0F;
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (!isPresent(source)) {
                continue;
            }
            float generated = Math.abs(source.getGeneratedSpeed());
            if (generated <= SoftDynamicsCore.EPSILON) {
                generated = Math.abs(access(source).cmp$getNominalSpeed());
            }
            omega0 = Math.max(omega0, generated);
        }
        if (omega0 <= SoftDynamicsCore.EPSILON) {
            return false;
        }

        float capacity = Math.max(0.0F, network.calculateCapacity());
        float k = 0.0F;
        for (Map.Entry<KineticBlockEntity, Float> entry : network.members.entrySet()) {
            KineticBlockEntity member = entry.getKey();
            if (!isPresent(member)) {
                continue;
            }
            float baseStress = entry.getValue() == null ? 0.0F : entry.getValue();
            if (baseStress <= 0.0F) {
                continue;
            }
            float nominal = Math.abs(access(member).cmp$getNominalSpeed());
            k += baseStress * nominal / omega0;
        }

        state.ratedSpeed = omega0;
        state.capacity = capacity;
        state.stressCoefficient = Math.max(0.0F, k);
        state.lastZone = state.core.zone();
        return true;
    }

    private static float seedOmega(KineticNetwork network, float omega0) {
        float displayed = 0.0F;
        float nominal = 0.0F;
        for (Map.Entry<KineticBlockEntity, Float> sourceEntry : network.sources.entrySet()) {
            KineticBlockEntity source = sourceEntry.getKey();
            if (!isPresent(source)) {
                continue;
            }
            float sourceNominal = Math.abs(access(source).cmp$getNominalSpeed());
            if (sourceNominal > nominal) {
                nominal = sourceNominal;
                displayed = Math.abs(access(source).cmp$getStoredSpeed());
            }
        }
        if (nominal <= SoftDynamicsCore.EPSILON || displayed <= SoftDynamicsCore.EPSILON) {
            return omega0;
        }
        float factor = displayed / nominal;
        if (!Float.isFinite(factor)) {
            return omega0;
        }
        return Math.min(omega0, omega0 * factor);
    }

    private static void applyFactor(KineticNetwork network, NetworkState state,
                                    float factor, DynamicsSettings settings, long gameTime) {
        state.network = network;
        state.members.clear();
        state.members.addAll(network.members.keySet());
        boolean sent = false;
        for (KineticBlockEntity member : state.members) {
            if (!isPresent(member)) {
                continue;
            }
            SoftSpeedAccess access = access(member);
            float nominal = access.cmp$getNominalSpeed();
            float target = nominal * factor;
            if (Math.abs(target) < SoftDynamicsCore.EPSILON) {
                target = 0.0F;
            }
            float previous = access.cmp$getStoredSpeed();
            access.cmp$applyDisplaySpeed(target);
            if (Math.abs(previous - target) >= settings.syncThreshold()
                    || state.lastSyncTick == Long.MIN_VALUE
                    || gameTime - state.lastSyncTick >= settings.syncInterval()) {
                member.sendData();
                sent = true;
            }
        }
        if (sent) {
            state.lastSyncTick = gameTime;
        }
    }

    private static void restoreAll() {
        for (Map.Entry<Level, Map<Long, NetworkState>> levelEntry : new ArrayList<>(STATES.entrySet())) {
            for (Map.Entry<Long, NetworkState> entry : new ArrayList<>(levelEntry.getValue().entrySet())) {
                NetworkState state = entry.getValue();
                for (KineticBlockEntity member : state.members) {
                    if (!isPresent(member)) {
                        continue;
                    }
                    SoftSpeedAccess access = access(member);
                    access.cmp$applyDisplaySpeed(access.cmp$getNominalSpeed());
                    member.sendData();
                }
                if (state.network != null) {
                    state.network.updateNetwork();
                }
                state.appliedFactor = 1.0F;
                state.core.reset();
                state.hasCache = false;
            }
            levelEntry.getValue().clear();
        }
    }

    public static void markDirty(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide() || be.network == null) {
            return;
        }
        Map<Long, NetworkState> levelStates = STATES.get(be.getLevel());
        if (levelStates == null) {
            return;
        }
        NetworkState state = levelStates.get(be.network);
        if (state != null) {
            state.cachedVersion = -1;
            state.hasCache = false;
        }
    }

    public static void topologyChanged(Level level, KineticBlockEntity be) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Map<Long, NetworkState> levelStates = STATES.get(level);
        if (levelStates == null) {
            return;
        }
        if (be != null && be.network != null) {
            NetworkState state = levelStates.get(be.network);
            if (state != null) {
                state.hasCache = false;
                state.cachedVersion = -1;
            }
        } else {
            levelStates.values().forEach(state -> {
                state.hasCache = false;
                state.cachedVersion = -1;
            });
        }
    }

    public static boolean shouldProtect(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide() || be.network == null) {
            return false;
        }
        Map<Long, NetworkState> levelStates = STATES.get(be.getLevel());
        if (levelStates == null) {
            return false;
        }
        NetworkState state = levelStates.get(be.network);
        if (state == null) {
            return false;
        }
        return state.core.stalled() || state.core.zone() == SoftDynamicsCore.Zone.STALLED;
    }

    /**
     * Whether this block entity belongs to a network the soft solver has
     * actually taken over. Used by the getSpeed bridge so unmanaged or broken
     * networks keep Create's original hard-overstress semantics.
     */
    public static boolean isManaged(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null) {
            return false;
        }
        if (be.getLevel().isClientSide()) {
            return CMPConfig.dynamics().enabled() && !CMPConfig.isSafeMode();
        }
        if (be.network == null) {
            return false;
        }
        Map<Long, NetworkState> levelStates = STATES.get(be.getLevel());
        if (levelStates == null) {
            return false;
        }
        NetworkState state = levelStates.get(be.network);
        return state != null && state.hasCache && state.core.initialised();
    }

    public static void onServerTick(MinecraftServer server) {
        if (!CMPConfig.dynamics().enabled() && wasActive) {
            restoreAll();
            wasActive = false;
        }
        long now = server.overworld() == null ? 0L : server.overworld().getGameTime();
        for (Map<Long, NetworkState> levelStates : STATES.values()) {
            levelStates.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 1200L);
        }
    }

    public static void clearLevel(Level level) {
        STATES.remove(level);
        SUSPENDED.remove(level);
    }

    /**
     * Temporarily keep the global solver away from a network while a component
     * controller is deliberately running a two-sided speed profile. The
     * suspension expires even if the component is removed mid-transition.
     */
    public static void suspend(Level level, long networkId, int ticks) {
        if (level == null || level.isClientSide() || networkId < 0L) {
            return;
        }
        long expiry = level.getGameTime() + Math.max(1, ticks);
        SUSPENDED.computeIfAbsent(level, ignored -> new HashMap<>()).put(networkId, expiry);
    }

    public static void resume(Level level, long networkId) {
        if (level == null) {
            return;
        }
        Map<Long, Long> suspended = SUSPENDED.get(level);
        if (suspended != null) {
            suspended.remove(networkId);
            if (suspended.isEmpty()) {
                SUSPENDED.remove(level);
            }
        }
        Map<Long, NetworkState> levelStates = STATES.get(level);
        if (levelStates != null) {
            NetworkState state = levelStates.get(networkId);
            if (state != null) {
                state.hasCache = false;
                state.cachedVersion = -1;
            }
        }
    }

    private static boolean isSuspended(Level level, long networkId) {
        Map<Long, Long> suspended = SUSPENDED.get(level);
        if (suspended == null) {
            return false;
        }
        Long expiry = suspended.get(networkId);
        if (expiry == null) {
            return false;
        }
        if (level.getGameTime() > expiry) {
            suspended.remove(networkId);
            if (suspended.isEmpty()) {
                SUSPENDED.remove(level);
            }
            return false;
        }
        return true;
    }

    public static void reset() {
        STATES.clear();
        SUSPENDED.clear();
        consecutiveErrors = 0;
        wasActive = false;
        budgetGameTime = Long.MIN_VALUE;
        budgetUsed = 0;
    }

    public static void invalidateCaches() {
        for (Map<Long, NetworkState> levelStates : STATES.values()) {
            for (NetworkState state : levelStates.values()) {
                state.hasCache = false;
                state.cachedVersion = -1;
            }
        }
    }

    public static List<Status> snapshot(Level onlyLevel) {
        List<Status> statuses = new ArrayList<>();
        for (Map.Entry<Level, Map<Long, NetworkState>> levelEntry : STATES.entrySet()) {
            Level level = levelEntry.getKey();
            if (onlyLevel != null && level != onlyLevel) {
                continue;
            }
            for (Map.Entry<Long, NetworkState> entry : levelEntry.getValue().entrySet()) {
                NetworkState state = entry.getValue();
                statuses.add(new Status(
                        level.dimension(),
                        entry.getKey(),
                        state.core.omega(),
                        state.lastTargetOmega,
                        state.capacity,
                        state.stressCoefficient,
                        state.stressCoefficient * state.core.omega(),
                        state.core.zone(),
                        state.core.overloadLatched(),
                        state.core.stalled(),
                        state.appliedFactor
                ));
            }
        }
        statuses.sort(Comparator.comparing(Status::dimension).thenComparingLong(Status::id));
        return statuses;
    }

    public static int trackedNetworks() {
        int total = 0;
        for (Map<Long, NetworkState> levelStates : STATES.values()) {
            total += levelStates.size();
        }
        return total;
    }

    private static int versionOf(KineticNetwork network) {
        if (network instanceof SoftNetworkAccess access) {
            return access.cmp$getTopologyVersion();
        }
        return network.members.size();
    }

    private static boolean isPresent(KineticBlockEntity be) {
        return be != null && be.getLevel() != null && be.getLevel().getBlockEntity(be.getBlockPos()) == be;
    }

    private static SoftSpeedAccess access(KineticBlockEntity be) {
        if (be instanceof SoftSpeedAccess access) {
            return access;
        }
        throw new IllegalStateException("KineticBlockEntity mixin did not apply");
    }

    private static void removeState(Level level, long id) {
        Map<Long, NetworkState> levelStates = STATES.get(level);
        if (levelStates != null) {
            levelStates.remove(id);
        }
    }

    public record Status(
            ResourceKey<Level> dimension,
            long id,
            float omega,
            float targetOmega,
            float capacity,
            float stressCoefficient,
            float stress,
            SoftDynamicsCore.Zone zone,
            boolean overloadLatched,
            boolean stalled,
            float factor
    ) {
    }

    private static final class NetworkState {
        private final SoftDynamicsCore.State core = new SoftDynamicsCore.State();
        private final List<KineticBlockEntity> members = new ArrayList<>();
        private KineticNetwork network;
        private float ratedSpeed;
        private float capacity;
        private float stressCoefficient;
        private int cachedVersion = -1;
        private long lastRebuild = Long.MIN_VALUE;
        private long lastTick = Long.MIN_VALUE;
        private long lastSeen;
        private long lastSyncTick = Long.MIN_VALUE;
        private float appliedFactor = 1.0F;
        private float lastTargetOmega;
        private SoftDynamicsCore.Zone lastZone = SoftDynamicsCore.Zone.STEADY;
        private boolean wasStalled;
        private boolean wasLatched;
        private boolean hasCache;
    }
}
