package dev.mineboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /mineboard} 指令树。
 */
public final class MineBoardCommands {
    private MineBoardCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mineboard")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("top")
                        .executes(context -> top(context.getSource(), 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(context -> top(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("me")
                        .executes(context -> me(context.getSource())))
                .then(Commands.literal("blocks")
                        .executes(context -> blocks(context.getSource(), null))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .executes(context -> blocks(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("display")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("sidebar")
                                .executes(context -> display(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> display(context.getSource(), false))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all")
                                .executes(context -> resetAll(context.getSource())))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .executes(context -> resetPlayer(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource()))));
    }

    // ------------------------------------------------------------------
    // 子指令
    // ------------------------------------------------------------------

    private static int help(CommandSourceStack source) {
        reply(source, """
                ⛏ 挖掘榜 / MineBoard
                /mineboard top [数量] — 查看排行榜（默认 10）
                /mineboard me — 查看自己的成绩与排名
                /mineboard blocks [玩家] — 查看方块明细
                /mineboard display sidebar|off — 开关侧边栏（管理员）
                /mineboard reset all|<玩家> — 清空数据（管理员）
                /mineboard reload — 重载配置（管理员）""");
        return 1;
    }

    private static int top(CommandSourceStack source, int limit) {
        List<MineBoardStats.Entry> ranking = MineBoard.get().stats().ranking();
        if (ranking.isEmpty()) {
            reply(source, "⛏ 挖掘榜还是空的——先去挖点方块吧。");
            return 0;
        }
        int shown = Math.min(limit, ranking.size());
        StringBuilder text = new StringBuilder();
        text.append("⛏ 挖掘榜 · 前 ").append(shown).append(" 名 / 共 ").append(ranking.size()).append(" 人");
        for (int i = 0; i < shown; i++) {
            MineBoardStats.Entry entry = ranking.get(i);
            text.append('\n').append(String.format(Locale.ROOT, "%2d. %-16s %,d", i + 1, entry.name, entry.total));
        }
        reply(source, text.toString());
        return shown;
    }

    private static int me(CommandSourceStack source) {
        String name = source.getTextName();
        MineBoardStats.Entry entry = MineBoard.get().stats().findByName(name);
        if (entry == null) {
            reply(source, "还没有你的挖掘记录——先去挖一个方块吧。");
            return 0;
        }
        List<MineBoardStats.Entry> ranking = MineBoard.get().stats().ranking();
        int rank = entry.rankIn(ranking);
        reply(source, String.format(Locale.ROOT,
                "⛏ %s：累计挖掘 %,d 个方块，排名 #%d / %d。",
                entry.name, entry.total, rank, ranking.size()));
        return 1;
    }

    private static int blocks(CommandSourceStack source, String playerArgument) {
        String target = playerArgument == null ? source.getTextName() : playerArgument.trim();
        MineBoardStats.Entry entry = MineBoard.get().stats().findByName(target);
        if (entry == null) {
            reply(source, "没有找到玩家「" + target + "」的挖掘记录。");
            return 0;
        }
        List<Map.Entry<String, Long>> topBlocks = entry.topBlocks(10);
        StringBuilder text = new StringBuilder();
        text.append("⛏ ").append(entry.name).append(" 的挖掘明细（共 ")
                .append(entry.blocks.size()).append(" 种方块，显示前 ").append(topBlocks.size()).append(" 种）");
        for (Map.Entry<String, Long> block : topBlocks) {
            text.append('\n').append(String.format(Locale.ROOT, "  %-24s %,d",
                    prettyBlock(block.getKey()), block.getValue()));
        }
        reply(source, text.toString());
        return topBlocks.size();
    }

    private static int display(CommandSourceStack source, boolean show) {
        MineBoard board = MineBoard.get();
        MinecraftServer server = source.getServer();
        board.ensureObjective(server);
        board.showSidebar(server, show);
        reply(source, show ? "已把挖掘榜挂到侧边栏。" : "已关闭侧边栏显示。");
        return 1;
    }

    private static int resetAll(CommandSourceStack source) {
        MineBoard board = MineBoard.get();
        MinecraftServer server = source.getServer();
        int before = board.stats().size();
        board.stats().clear();
        board.forgetSyncState();
        board.runCommand(server, "scoreboard objectives remove " + MineBoard.OBJECTIVE);
        board.ensureObjective(server);
        if (board.config().showSidebarOnStart) {
            board.showSidebar(server, true);
        }
        board.stats().save();
        reply(source, "已清空全部挖掘数据（" + before + " 名玩家）。");
        return 1;
    }

    private static int resetPlayer(CommandSourceStack source, String playerArgument) {
        MineBoard board = MineBoard.get();
        MinecraftServer server = source.getServer();
        String target = playerArgument.trim();
        MineBoardStats.Entry removed = board.stats().removeByName(target);
        if (removed == null) {
            reply(source, "没有找到玩家「" + target + "」的挖掘记录。");
            return 0;
        }
        board.forgetSyncState();
        board.resetScore(server, removed.name);
        board.stats().save();
        reply(source, String.format(Locale.ROOT, "已清空 %s 的挖掘记录（原有 %,d 个方块）。",
                removed.name, removed.total));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        MineBoard board = MineBoard.get();
        board.reloadConfig();
        board.ensureObjective(source.getServer());
        reply(source, "配置已重新加载。");
        return 1;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static String prettyBlock(String blockId) {
        String id = blockId == null ? "" : blockId;
        if (id.startsWith("block.")) {
            id = id.substring("block.".length());
        }
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return id;
    }
}
