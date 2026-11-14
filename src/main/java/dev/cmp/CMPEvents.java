package dev.cmp;

import dev.cmp.config.CMPConfig;
import dev.cmp.dynamics.SoftNetworkManager;
import dev.cmp.hotplug.HotPlugGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus glue. Deliberately thin; the solver and component controllers live
 * in their own packages.
 */
public final class CMPEvents {
    private CMPEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CMPCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        if (!(event.getPlacedBlock().getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate)) {
            return;
        }
        HotPlugGuard.schedule(level, event.getPos(), event.getEntity());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        HotPlugGuard.processPending(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        SoftNetworkManager.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            SoftNetworkManager.clearLevel(level);
            HotPlugGuard.clear(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (server != null) {
            CreateMechanicalPrinciples.onServerStopping();
        }
        CMPConfig.clearRuntimePreset();
    }
}
