package dev.cmp.config;

import dev.cmp.CreateMechanicalPrinciples;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

/**
 * NeoForge config split: core solver tuning is COMMON, world behaviour and the
 * hot-plug threshold are SERVER. A runtime preset override exists so
 * {@code /cmp preset <name>} can be used for in-game experiments without
 * rewriting the config file.
 */
public final class CMPConfig {
    private static final Logger LOGGER = CreateMechanicalPrinciples.LOGGER;

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    // [dynamics]
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<DynamicsPreset> PRESET;
    public static final ModConfigSpec.DoubleValue ALPHA;
    public static final ModConfigSpec.DoubleValue ACCEL_CEILING_UP;
    public static final ModConfigSpec.DoubleValue ACCEL_CEILING_DOWN;
    public static final ModConfigSpec.DoubleValue ACCEL_FLOOR;
    public static final ModConfigSpec.DoubleValue STALL_RPM;
    public static final ModConfigSpec.DoubleValue STALL_RECOVERY;
    public static final ModConfigSpec.DoubleValue OVERLOAD_RELEASE;
    public static final ModConfigSpec.DoubleValue K_EPSILON;
    public static final ModConfigSpec.IntValue SLEEP_TICKS;
    public static final ModConfigSpec.DoubleValue SLEEP_EPSILON;
    public static final ModConfigSpec.IntValue MAX_COMPONENTS;
    public static final ModConfigSpec.IntValue TOPOLOGY_COOLDOWN;
    public static final ModConfigSpec.DoubleValue SYNC_THRESHOLD;
    public static final ModConfigSpec.IntValue SYNC_INTERVAL;
    public static final ModConfigSpec.DoubleValue CLIENT_INTERPOLATION;
    public static final ModConfigSpec.BooleanValue AUTO_SAFE_MODE;

    // [components]
    public static final ModConfigSpec.BooleanValue CLUTCH_SOFT_START;
    public static final ModConfigSpec.IntValue CLUTCH_ENGAGE_DELAY;
    public static final ModConfigSpec.DoubleValue SYNC_EPSILON;
    public static final ModConfigSpec.BooleanValue AUTO_MERGE_ON_SYNC;
    public static final ModConfigSpec.DoubleValue REVERSE_ACCEL;
    public static final ModConfigSpec.IntValue REVERSE_TRANSITION;
    public static final ModConfigSpec.BooleanValue REFUSE_REVERSE_FOREIGN_SOURCE;
    public static final ModConfigSpec.DoubleValue SEQUENCER_ACCEL;
    public static final ModConfigSpec.IntValue SEQUENCER_STARTUP;
    public static final ModConfigSpec.DoubleValue OVERLOAD_REPLAN;
    public static final ModConfigSpec.BooleanValue MAINTAIN_RATIO_AFTER_STEP;
    public static final ModConfigSpec.DoubleValue CHAIN_RATIO_DELTA;
    public static final ModConfigSpec.BooleanValue SPEED_CONTROLLER_THROTTLE;

    // [hotplug]
    public static final ModConfigSpec.IntValue HOT_PLUG_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_ON_CONTRAPTIONS;

    private static volatile DynamicsSettings cachedDynamics;
    private static volatile ComponentSettings cachedComponents;
    private static volatile DynamicsPreset runtimePreset;
    private static volatile boolean safeModeTriggered;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        common.comment("Soft-dynamics core. All speeds are in RPM, stress in SU.")
                .push("dynamics");
        ENABLED = common.comment("Master switch. When false, Create's original hard overstress behaviour is used.")
                .define("enabled", true);
        PRESET = common.comment("REALISTIC / BALANCED / ARCADE / CUSTOM. Non-CUSTOM presets override alpha and acceleration fields.")
                .defineEnum("preset", DynamicsPreset.BALANCED);
        ALPHA = common.comment("Response coefficient in a = alpha * (C - S) / K.")
                .defineInRange("alpha", 0.05D, DynamicsSettings.MIN_ALPHA, DynamicsSettings.MAX_ALPHA);
        ACCEL_CEILING_UP = common.comment("Upward acceleration ceiling as a fraction of no-load source RPM per tick.")
                .defineInRange("accelCeilingUpMultiplier", 0.20D, 0.001D, 10.0D);
        ACCEL_CEILING_DOWN = common.comment("Downward acceleration ceiling as a fraction of no-load source RPM per tick.")
                .defineInRange("accelCeilingDownMultiplier", 0.30D, 0.001D, 10.0D);
        ACCEL_FLOOR = common.comment("Minimum non-zero acceleration as a fraction of source RPM per tick.")
                .defineInRange("accelFloorMultiplier", 0.01D, 0.0D, 10.0D);
        STALL_RPM = common.comment("Stall when equilibrium speed falls below this fraction of source RPM.")
                .defineInRange("stallRpmMultiplier", 0.10D, 0.0D, 1.0D);
        STALL_RECOVERY = common.comment("Stall clears when equilibrium speed exceeds stallRpmMultiplier * this.")
                .defineInRange("stallRecoveryMultiplier", 1.5D, 1.0D, 10.0D);
        OVERLOAD_RELEASE = common.comment("Overload latch releases below this fraction of capacity.")
                .defineInRange("overloadReleaseRatio", 0.95D, 0.50D, 1.0D);
        K_EPSILON = common.comment("Numerical floor for K to avoid division by zero.")
                .defineInRange("kEpsilon", 1.0E-4D, 1.0E-6D, 1.0D);
        SLEEP_TICKS = common.comment("Ticks at equilibrium before a network sleeps.")
                .defineInRange("sleepTicks", 40, 1, 20 * 60 * 5);
        SLEEP_EPSILON = common.comment("Speed error considered settled, in RPM.")
                .defineInRange("sleepEpsilon", 0.01D, 0.0D, 10.0D);
        MAX_COMPONENTS = common.comment("Maximum active components solved per tick.")
                .defineInRange("maxComponentsPerTick", 8, 1, 4096);
        TOPOLOGY_COOLDOWN = common.comment("Minimum ticks between topology rebuilds.")
                .defineInRange("topologyRebuildCooldown", 10, 0, 200);
        SYNC_THRESHOLD = common.comment("Minimum speed delta before pushing a block entity update, in RPM.")
                .defineInRange("syncThreshold", 0.1D, 0.0D, 64.0D);
        SYNC_INTERVAL = common.comment("Forced client sync interval in ticks.")
                .defineInRange("syncInterval", 20, 1, 1200);
        CLIENT_INTERPOLATION = common.comment("Client-side display interpolation factor.")
                .defineInRange("speedInterpolation", 0.3D, 0.0D, 1.0D);
        AUTO_SAFE_MODE = common.comment("Disable soft dynamics automatically after repeated internal errors.")
                .define("automaticSafeMode", true);
        common.pop();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.comment("Component behaviour and rigid-connection rules. Applied per world.")
                .push("components");
        CLUTCH_SOFT_START = server.comment("Enable the clutch soft-start state machine.")
                .define("clutchSoftStart", true);
        CLUTCH_ENGAGE_DELAY = server.comment("Ticks the clutch output stays disengaged while starting.")
                .defineInRange("clutchEngageDelayTicks", 5, 0, 200);
        SYNC_EPSILON = server.comment("Speed difference at which a soft-start component is considered synced, in RPM.")
                .defineInRange("syncEpsilon", 0.5D, 0.0001D, 64.0D);
        AUTO_MERGE_ON_SYNC = server.comment("Merge the two sides into one rigid network once synced.")
                .define("autoMergeOnSync", true);
        REVERSE_ACCEL = server.comment("Gearshift transition acceleration multiplier (clamped to 3.0).")
                .defineInRange("reverseAccelMultiplier", 1.5D, 1.0D, 3.0D);
        REVERSE_TRANSITION = server.comment("Ticks spent at zero output before reversing.")
                .defineInRange("reverseTransitionTicks", 5, 0, 200);
        REFUSE_REVERSE_FOREIGN_SOURCE = server.comment("Refuse a redstone reversal when the output side has another source.")
                .define("refuseReverseWithForeignSource", true);
        SEQUENCER_ACCEL = server.comment("Fixed sequencer acceleration in RPM/tick.")
                .defineInRange("sequencerAcceleration", 0.5D, 0.001D, 256.0D);
        SEQUENCER_STARTUP = server.comment("Extra zero-output ticks before a sequencer step accelerates.")
                .defineInRange("sequencerStartupTicks", 1, 0, 200);
        OVERLOAD_REPLAN = server.comment("Pause a sequencer step when actual speed falls below this fraction of plan.")
                .defineInRange("overloadReplanThreshold", 0.5D, 0.0D, 1.0D);
        MAINTAIN_RATIO_AFTER_STEP = server.comment("Keep the step ratio after a step instead of chasing input speed.")
                .define("maintainRatioAfterStep", false);
        CHAIN_RATIO_DELTA = server.comment("Maximum adjustable chain ratio change per tick.")
                .defineInRange("maxRatioDelta", 0.1D, 0.0001D, 4.0D);
        SPEED_CONTROLLER_THROTTLE = server.comment("Reduce rotation speed controller output when the network is overloaded.")
                .define("rotationSpeedControllerSoftThrottle", true);
        server.pop();

        server.comment("Hot-plug and rigid connection rules").push("hotplug");
        HOT_PLUG_THRESHOLD = server.comment("Reject attaching a component to a running network above this RPM. 0 disables hot-plug, 256 removes the limit.")
                .defineInRange("hotPlugSpeedThreshold", 64, 0, 256);
        ENABLE_ON_CONTRAPTIONS = server.comment("Reserved. Soft dynamics on moving contraptions is not implemented in 1.0.")
                .define("enableOnContraptions", false);
        server.pop();

        COMMON_SPEC = common.build();
        SERVER_SPEC = server.build();
    }

    private CMPConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static DynamicsSettings dynamics() {
        DynamicsSettings local = cachedDynamics;
        if (local != null) {
            return local;
        }
        DynamicsPreset preset = runtimePreset != null ? runtimePreset : PRESET.get();
        float alpha = ALPHA.get().floatValue();
        float up = ACCEL_CEILING_UP.get().floatValue();
        float down = ACCEL_CEILING_DOWN.get().floatValue();
        float floor = ACCEL_FLOOR.get().floatValue();
        if (preset != DynamicsPreset.CUSTOM) {
            alpha = preset.alpha();
            up = preset.accelCeilingUp();
            down = preset.accelCeilingDown();
            floor = preset.accelFloor();
        } else {
            if (floor > Math.min(up, down)) {
                LOGGER.warn("accelFloorMultiplier ({}) exceeds an acceleration ceiling (up={}, down={}); clamping.",
                        floor, up, down);
            }
        }
        boolean enabled = ENABLED.get() && !safeModeTriggered;
        DynamicsSettings settings = new DynamicsSettings(
                enabled,
                alpha,
                up,
                down,
                floor,
                STALL_RPM.get().floatValue(),
                STALL_RECOVERY.get().floatValue(),
                OVERLOAD_RELEASE.get().floatValue(),
                K_EPSILON.get().floatValue(),
                SLEEP_TICKS.get(),
                SLEEP_EPSILON.get().floatValue(),
                MAX_COMPONENTS.get(),
                TOPOLOGY_COOLDOWN.get(),
                SYNC_THRESHOLD.get().floatValue(),
                SYNC_INTERVAL.get(),
                CLIENT_INTERPOLATION.get().floatValue(),
                AUTO_SAFE_MODE.get() && safeModeTriggered
        );
        cachedDynamics = settings;
        return settings;
    }

    public static ComponentSettings components() {
        ComponentSettings local = cachedComponents;
        if (local != null) {
            return local;
        }
        ComponentSettings settings = new ComponentSettings(
                CLUTCH_SOFT_START.get(),
                CLUTCH_ENGAGE_DELAY.get(),
                SYNC_EPSILON.get().floatValue(),
                AUTO_MERGE_ON_SYNC.get(),
                REVERSE_ACCEL.get().floatValue(),
                REVERSE_TRANSITION.get(),
                REFUSE_REVERSE_FOREIGN_SOURCE.get(),
                SEQUENCER_ACCEL.get().floatValue(),
                SEQUENCER_STARTUP.get(),
                OVERLOAD_REPLAN.get().floatValue(),
                MAINTAIN_RATIO_AFTER_STEP.get(),
                CHAIN_RATIO_DELTA.get().floatValue(),
                SPEED_CONTROLLER_THROTTLE.get(),
                HOT_PLUG_THRESHOLD.get(),
                ENABLE_ON_CONTRAPTIONS.get()
        );
        cachedComponents = settings;
        return settings;
    }

    public static void setRuntimePreset(DynamicsPreset preset) {
        runtimePreset = preset;
        safeModeTriggered = false;
        cachedDynamics = null;
        LOGGER.info("Soft-dynamics runtime preset set to {}", preset);
    }

    public static void clearRuntimePreset() {
        runtimePreset = null;
        safeModeTriggered = false;
        cachedDynamics = null;
        cachedComponents = null;
    }

    public static DynamicsPreset runtimePreset() {
        return runtimePreset;
    }

    public static void enterSafeMode() {
        if (safeModeTriggered) {
            return;
        }
        safeModeTriggered = true;
        cachedDynamics = null;
        LOGGER.error("Soft dynamics entered safe mode after repeated internal errors. Original Create overstress behaviour is active. Use /cmp reload to retry.");
    }

    public static boolean isSafeMode() {
        return safeModeTriggered;
    }

    public static void invalidate() {
        cachedDynamics = null;
        cachedComponents = null;
    }
}
