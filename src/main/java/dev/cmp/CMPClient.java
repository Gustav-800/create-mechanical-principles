package dev.cmp;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only extension point for the standard NeoForge config screen. Keeping
 * the screen registration here avoids loading client classes on a dedicated
 * server.
 */
@Mod(value = CreateMechanicalPrinciples.MOD_ID, dist = Dist.CLIENT)
public final class CMPClient {
    public CMPClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ConfigurationScreen(container, parent));
    }
}
