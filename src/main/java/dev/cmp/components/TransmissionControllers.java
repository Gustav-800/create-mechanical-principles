package dev.cmp.components;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-block-entity controller registry. Weak keys avoid keeping unloaded block
 * entities alive; all access is on the server thread.
 */
public final class TransmissionControllers {
    private static final Map<KineticBlockEntity, ClutchController> CLUTCHES = new WeakHashMap<>();
    private static final Map<KineticBlockEntity, GearshiftController> GEARSHIFTS = new WeakHashMap<>();

    private TransmissionControllers() {
    }

    public static void tick(KineticBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (be instanceof ClutchBlockEntity clutch) {
            CLUTCHES.computeIfAbsent(clutch, ignored -> new ClutchController(clutch)).tick();
        } else if (be instanceof GearshiftBlockEntity gearshift) {
            GEARSHIFTS.computeIfAbsent(gearshift, ignored -> new GearshiftController(gearshift)).tick();
        }
    }

    public static ClutchController clutch(ClutchBlockEntity clutch) {
        return CLUTCHES.computeIfAbsent(clutch, ignored -> new ClutchController(clutch));
    }

    public static GearshiftController gearshift(GearshiftBlockEntity gearshift) {
        return GEARSHIFTS.computeIfAbsent(gearshift, ignored -> new GearshiftController(gearshift));
    }

    public static float clutchModifier(ClutchBlockEntity clutch, net.minecraft.core.Direction face, float original) {
        return clutch(clutch).adjustModifier(face, original);
    }

    public static float gearshiftModifier(GearshiftBlockEntity gearshift, net.minecraft.core.Direction face,
                                          float original) {
        return gearshift(gearshift).adjustModifier(face, original);
    }

    public static float speedControllerOutput(SpeedControllerBlockEntity controller, float original) {
        return SpeedControllerLimiter.limit(controller, original);
    }

    public static void clear() {
        CLUTCHES.clear();
        GEARSHIFTS.clear();
    }
}
