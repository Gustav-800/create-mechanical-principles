package dev.cmp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.cmp.config.CMPConfig;
import dev.cmp.config.DynamicsPreset;
import dev.cmp.dynamics.SoftNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Small administration surface intended for in-game verification:
 * {@code /cmp status}, {@code /cmp reload}, {@code /cmp preset <name>}.
 */
public final class CMPCommands {
    private static final SuggestionProvider<CommandSourceStack> PRESETS =
            (context, builder) -> {
                for (DynamicsPreset preset : DynamicsPreset.values()) {
                    builder.suggest(preset.name().toLowerCase(Locale.ROOT));
                }
                return builder.buildFuture();
            };

    private CMPCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cmp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(CMPCommands::status))
                .then(Commands.literal("reload").executes(CMPCommands::reload))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PRESETS)
                                .executes(CMPCommands::preset))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<SoftNetworkManager.Status> statuses = SoftNetworkManager.snapshot(level);
        source.sendSuccess(() -> Component.literal("Create: Mechanical Principles — soft dynamics status")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
                "Preset=" + (CMPConfig.runtimePreset() == null ? "config" : CMPConfig.runtimePreset())
                        + "  safeMode=" + CMPConfig.isSafeMode()
                        + "  tracked=" + SoftNetworkManager.trackedNetworks()), false);
        if (statuses.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active kinetic networks in this dimension."), false);
            return 1;
        }
        for (SoftNetworkManager.Status status : statuses) {
            String line = String.format(Locale.ROOT,
                    "#%d  ω=%.2f/%.2f rpm  factor=%.3f  C=%.1f  K=%.3f  S=%.1f  %s%s%s",
                    status.id(), status.omega(), status.targetOmega(), status.factor(),
                    status.capacity(), status.stressCoefficient(), status.stress(),
                    status.zone(),
                    status.overloadLatched() ? " [latched]" : "",
                    status.stalled() ? " [stalled]" : "");
            source.sendSuccess(() -> Component.literal(line).withStyle(status.stalled()
                    ? ChatFormatting.RED
                    : status.overloadLatched() ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
        }
        return statuses.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CMPConfig.clearRuntimePreset();
        SoftNetworkManager.invalidateCaches();
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared the runtime preset and invalidated soft-dynamics caches. "
                        + "Use /reload or restart the server to reread files on disk."), true);
        return 1;
    }

    private static int preset(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name").toUpperCase(Locale.ROOT);
        DynamicsPreset preset;
        try {
            preset = DynamicsPreset.valueOf(name);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(
                    "Unknown preset. Choose one of " + Arrays.toString(DynamicsPreset.values())));
            return 0;
        }
        CMPConfig.setRuntimePreset(preset);
        SoftNetworkManager.invalidateCaches();
        context.getSource().sendSuccess(() -> Component.literal(
                "Soft-dynamics preset set to " + preset + " (runtime only)."), true);
        return 1;
    }
}
