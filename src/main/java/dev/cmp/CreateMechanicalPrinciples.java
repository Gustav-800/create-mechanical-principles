package dev.cmp;

import dev.cmp.config.CMPConfig;
import dev.cmp.dynamics.SoftNetworkManager;
import dev.cmp.hotplug.HotPlugGuard;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create: Mechanical Principles.
 *
 * <p>Replaces Create's binary overstress stop with a per-network soft dynamics
 * solver while preserving Create's own network topology and propagation code.</p>
 */
@Mod(CreateMechanicalPrinciples.MOD_ID)
public final class CreateMechanicalPrinciples {
    public static final String MOD_ID = "create_mechanical_principles";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateMechanicalPrinciples(IEventBus modEventBus, ModContainer modContainer) {
        CMPConfig.register(modContainer);
        NeoForge.EVENT_BUS.register(CMPEvents.class);
        LOGGER.info("Create: Mechanical Principles loaded; soft dynamics attached to Create's kinetic networks.");
    }

    public static void onServerStopping() {
        SoftNetworkManager.reset();
        HotPlugGuard.clear();
        dev.cmp.components.TransmissionControllers.clear();
    }
}
