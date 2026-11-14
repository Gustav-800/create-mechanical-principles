package dev.cmp.components;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.ComponentSettings;

/**
 * First-version speed controller adaptation: the selected output speed is a
 * target ceiling, not an absolute constraint. When the controller's network is
 * over capacity, the requested output is scaled by the available stress ratio.
 */
public final class SpeedControllerLimiter {
    private SpeedControllerLimiter() {
    }

    public static float limit(SpeedControllerBlockEntity controller, float desired) {
        ComponentSettings settings = CMPConfig.components();
        if (!settings.speedControllerSoftThrottle() || !Float.isFinite(desired) || desired == 0.0F) {
            return desired;
        }
        KineticNetwork network = controller.getOrCreateNetwork();
        if (network == null || !network.initialized) {
            return desired;
        }
        float capacity = network.calculateCapacity();
        if (capacity <= 0.0F) {
            return 0.0F;
        }
        float stress = network.calculateStress();
        if (stress <= capacity) {
            return desired;
        }
        float ratio = capacity / stress;
        return desired * Math.max(0.0F, Math.min(1.0F, ratio));
    }
}
