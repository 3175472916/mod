package dev.mineboard;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 与 Minecraft 版本强相关、最容易因版本更名而需要修改的几处调用，
 * 全部集中在这里，方便单点升级。
 */
public final class Bridge {
    private Bridge() {
    }

    /** 玩家的计分板用名（也是指令里使用的名字）。 */
    public static String playerName(Player player) {
        return player.getName().getString();
    }

    /** 方块的注册名，例如 {@code minecraft:stone}。 */
    public static String blockId(BlockState state) {
        try {
            var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (key != null) {
                return key.toString();
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底方案
        }
        return state.getBlock().getDescriptionId();
    }

    /** 当前在线玩家。 */
    public static List<Player> onlinePlayers(MinecraftServer server) {
        return new ArrayList<>(server.getPlayerList().getPlayers());
    }
}
