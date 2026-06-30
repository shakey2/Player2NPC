package com.goodbird.player2npc.companion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.PlayerBudgetConfigHolder;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.config.PlayerBudgetConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Budget sub-commands under {@code /player2npc budget} (permission 0).
 *
 * <p>These commands read and write the <em>same</em> config source that the PlayerEngine A4
 * enforcement gate consults, so that what {@code status} reports and what {@code set*} changes
 * always matches what is actually enforced:
 * <ul>
 *   <li>Integrated singleplayer and any non-dedicated / non-{@code PROMPTER_PAYS} context use the
 *       server config {@code server_player2.json} via {@link Player2ServerConfigHolder} — the file
 *       the gate enforces in those contexts (see
 *       {@link BudgetThresholdsResolver#useServerConfigForBudget(MinecraftServer)}).</li>
 *   <li>Dedicated {@code PROMPTER_PAYS} uses the executing player's {@link PlayerBudgetConfig}
 *       stored at {@code player2npc/persistentdata/owners/<uuid>/player-budget.json}.</li>
 * </ul>
 *
 * <p>Server-global budget limits are also manageable by OPs via {@code /playerengine player2 budget}.
 */
public final class BudgetCommands {

    private BudgetCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("budget")
                .then(Commands.literal("soft")
                        .then(Commands.argument("calls", IntegerArgumentType.integer(0))
                                .executes(ctx -> setSoft(ctx, IntegerArgumentType.getInteger(ctx, "calls")))))
                .then(Commands.literal("hard")
                        .then(Commands.argument("calls", IntegerArgumentType.integer(0))
                                .executes(ctx -> setHard(ctx, IntegerArgumentType.getInteger(ctx, "calls")))))
                .then(Commands.literal("window")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(ctx -> setWindow(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("joules_soft")
                        .then(Commands.argument("joules", IntegerArgumentType.integer(0))
                                .executes(ctx -> setJoulesSoft(ctx, IntegerArgumentType.getInteger(ctx, "joules")))))
                .then(Commands.literal("joules_hard")
                        .then(Commands.argument("joules", IntegerArgumentType.integer(0))
                                .executes(ctx -> setJoulesHard(ctx, IntegerArgumentType.getInteger(ctx, "joules")))))
                .then(Commands.literal("joules_refresh")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(60, 86400))
                                .executes(ctx -> setJoulesRefresh(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("reset").executes(BudgetCommands::reset))
                .then(Commands.literal("status").executes(BudgetCommands::status));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /**
     * True when budget settings should be read/written on the server config ({@code server_player2.json})
     * — the source the A4 gate enforces — rather than the per-player file. Mirrors
     * {@code BudgetConfigCommands.resolveTarget} in PlayerEngine: server config wins on integrated
     * singleplayer (non-dedicated) and whenever payerMode is not dedicated {@code PROMPTER_PAYS}.
     */
    private static boolean useServerConfig(MinecraftServer server) {
        return BudgetThresholdsResolver.usesServerBudgetStore(server);
    }

    private static int setSoft(CommandContext<CommandSourceStack> ctx, int calls) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setSoftBudgetCallsPerWindow(calls);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setSoftBudgetCallsPerWindow(calls);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        MutableComponent msgComp = (calls == 0
                ? Component.translatable("command.player2npc.budget.soft.disabled")
                : Component.translatable("command.player2npc.budget.soft.set", calls))
                .withStyle(ChatFormatting.GREEN);
        ctx.getSource().sendSuccess(() -> msgComp, false);
        return 1;
    }

    private static int setHard(CommandContext<CommandSourceStack> ctx, int calls) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setHardBudgetCallsPerWindow(calls);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setHardBudgetCallsPerWindow(calls);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        MutableComponent msgComp = (calls == 0
                ? Component.translatable("command.player2npc.budget.hard.disabled")
                : Component.translatable("command.player2npc.budget.hard.set", calls))
                .withStyle(ChatFormatting.GREEN);
        ctx.getSource().sendSuccess(() -> msgComp, false);
        return 1;
    }

    private static int setWindow(CommandContext<CommandSourceStack> ctx, int minutes) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setBudgetWindowMinutes(minutes);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setBudgetWindowMinutes(minutes);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        // Reset call window so new window starts from now
        BudgetTracker.reset(player.getUUID().toString());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.window.set", minutes)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setJoulesSoft(CommandContext<CommandSourceStack> ctx, int joules) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setSoftJoulesThreshold(joules);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setSoftJoulesThreshold(joules);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        MutableComponent msgComp = (joules == 0
                ? Component.translatable("command.player2npc.budget.joules_soft.disabled")
                : Component.translatable("command.player2npc.budget.joules_soft.set", joules))
                .withStyle(ChatFormatting.GREEN);
        ctx.getSource().sendSuccess(() -> msgComp, false);
        return 1;
    }

    private static int setJoulesHard(CommandContext<CommandSourceStack> ctx, int joules) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setHardJoulesThreshold(joules);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setHardJoulesThreshold(joules);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        MutableComponent msgComp = (joules == 0
                ? Component.translatable("command.player2npc.budget.joules_hard.disabled")
                : Component.translatable("command.player2npc.budget.joules_hard.set", joules))
                .withStyle(ChatFormatting.GREEN);
        ctx.getSource().sendSuccess(() -> msgComp, false);
        return 1;
    }

    private static int setJoulesRefresh(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (useServerConfig(server)) {
            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            cfg.setJoulesRefreshIntervalSeconds(seconds);
            Player2ServerConfigHolder.validateAndFix(cfg);
            Player2ServerConfigHolder.save();
        } else {
            PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(server, player.getUUID());
            cfg.setJoulesRefreshIntervalSeconds(seconds);
            PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.joules_refresh.set", seconds)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        String key = player.getUUID().toString();
        BudgetTracker.reset(key);
        JoulesCache.invalidate(key);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.reset.success")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        String key = player.getUUID().toString();

        boolean serverFile = useServerConfig(server);
        BudgetThresholds cfg = serverFile
                ? Player2ServerConfigHolder.get()
                : PlayerBudgetConfigHolder.load(server, player.getUUID());
        Optional<JoulesCache.JoulesSnapshot> snapOpt = JoulesCache.get(key);

        MutableComponent header = Component.translatable("command.player2npc.budget.status.header").withStyle(ChatFormatting.GOLD);
        ctx.getSource().sendSuccess(() -> header, false);

        String fileNote = serverFile ? "server_player2.json" : "player-budget.json";
        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_limits_from")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(fileNote).withStyle(ChatFormatting.WHITE)), false);

        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_call_limits")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("command.player2npc.budget.status.call_limits_value",
                        fmtLimit(cfg.getSoftBudgetCallsPerWindow()),
                        fmtLimit(cfg.getHardBudgetCallsPerWindow()),
                        String.valueOf(cfg.getBudgetWindowMinutes())
                ).withStyle(ChatFormatting.WHITE)), false);

        ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_joules_limits")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("command.player2npc.budget.status.joules_limits_value",
                        fmtLimit(cfg.getSoftJoulesThreshold()),
                        fmtLimit(cfg.getHardJoulesThreshold()),
                        String.valueOf(cfg.getJoulesRefreshIntervalSeconds())
                ).withStyle(ChatFormatting.WHITE)), false);

        BudgetTracker.WindowSnapshot winSnap = BudgetTracker.statusSnapshot(cfg).get(key);
        if (winSnap != null) {
            long remaining = Math.max(0, winSnap.windowEndMs() - System.currentTimeMillis()) / 1000L;
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_calls_window")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("command.player2npc.budget.status.calls_window_active",
                            winSnap.callCount(), remaining
                    ).withStyle(ChatFormatting.WHITE)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_calls_window")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("command.player2npc.budget.status.calls_window_none").withStyle(ChatFormatting.WHITE)), false);
        }

        if (snapOpt.isPresent()) {
            JoulesCache.JoulesSnapshot snap = snapOpt.get();
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_joules_cached")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("command.player2npc.budget.status.joules_display",
                            snap.joulesDisplay()).withStyle(ChatFormatting.WHITE)), false);
            if (!snap.patronTier.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_patron_tier")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(snap.patronTier).withStyle(ChatFormatting.AQUA)), false);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.player2npc.budget.status.label_joules")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("command.player2npc.budget.status.joules_unfetched")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        return 1;
    }

    private static Component fmtLimit(int val) {
        return val == 0
                ? Component.translatable("command.player2npc.budget.limit_off")
                : Component.literal(String.valueOf(val));
    }
}
