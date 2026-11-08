package dev.mineboard;

import com.mojang.brigadier.CommandDispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端挖掘计分板。
 *
 * <p>工作方式：
 * <ol>
 *     <li>监听 {@link PlayerBlockBreakEvents#AFTER}，记录每次玩家破坏方块；</li>
 *     <li>累计值保存在 {@link MineBoardStats}（JSON，随服务器配置目录）；</li>
 *     <li>用原版 {@code /scoreboard} 指令把总分镜像到名为 {@code mineboard} 的 dummy 计分板；</li>
 *     <li>侧边栏直接显示该计分板——原版会自动按分数从高到低排序，因此不需要自己渲染排行榜。</li>
 * </ol>
 */
public final class MineBoard implements ModInitializer {
    public static final String MOD_ID = "mineboard";
    /** 计分板目标名，也是指令里使用的名字。 */
    public static final String OBJECTIVE = "mineboard";
    /** 计分板显示标题（指令里的带引号文本组件）。 */
    public static final String OBJECTIVE_TITLE = "\"⛏ 挖掘榜\"";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MineBoard instance;

    private MineBoardConfig config;
    private MineBoardStats stats;
    /** 已经写进计分板的值，避免每秒重复下发同样的指令。 */
    private final Map<UUID, Long> lastSynced = new HashMap<>();

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private int tickCounter;

    /**
     * Fabric 会用 {@code Class#getDeclaredConstructor()} 反射实例化入口类，
     * 所以必须保留一个无参构造。
     */
    public MineBoard() {
    }

    public static MineBoard get() {
        return instance;
    }

    public MineBoardConfig config() {
        return config;
    }

    public MineBoardStats stats() {
        return stats;
    }

    public void reloadConfig() {
        this.config = MineBoardConfig.load();
    }

    /** 重置数据后调用，强制下一次同步重新写分。 */
    public void forgetSyncState() {
        lastSynced.clear();
    }

    @Override
    public void onInitialize() {
        instance = this;
        this.config = MineBoardConfig.load();
        this.stats = MineBoardStats.load();

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                instance.onBlockBroken(player, state));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            instance.dispatcher = dispatcher;
            MineBoardCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            instance.ensureObjective(server);
            if (instance.config.showSidebarOnStart) {
                instance.showSidebar(server, true);
            }
            instance.syncAll(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            instance.syncAll(server);
            instance.stats.save();
        });

        ServerTickEvents.END_SERVER_TICK.register(instance::onTick);

        LOGGER.info("[MineBoard] 已加载，目前记录 {} 名玩家", instance.stats.size());
    }

    // ------------------------------------------------------------------
    // 计分
    // ------------------------------------------------------------------

    private void onBlockBroken(Player player, BlockState state) {
        if (player == null || state == null) {
            return;
        }
        if (player.isSpectator() && !config.countSpectator) {
            return;
        }
        if (player.isCreative() && !config.countCreative) {
            return;
        }
        if (state.isAir()) {
            return;
        }
        String blockId = Bridge.blockId(state);
        if (blockId == null || config.isIgnored(blockId)) {
            return;
        }
        stats.record(player.getUUID().toString(), Bridge.playerName(player), blockId);
    }

    private void onTick(MinecraftServer server) {
        if (++tickCounter < Math.max(1, config.updateIntervalTicks)) {
            return;
        }
        tickCounter = 0;
        syncAll(server);
    }

    /** 把在线玩家的总分同步到原版计分板。 */
    public void syncAll(MinecraftServer server) {
        for (Player player : Bridge.onlinePlayers(server)) {
            syncPlayer(server, player);
        }
    }

    private void syncPlayer(MinecraftServer server, Player player) {
        UUID id = player.getUUID();
        long total = stats.total(id.toString());
        Long previous = lastSynced.get(id);
        if (previous != null && previous == total) {
            return;
        }
        setScore(server, Bridge.playerName(player), total);
        lastSynced.put(id, total);
    }

    // ------------------------------------------------------------------
    // 计分板指令封装
    // ------------------------------------------------------------------

    public void ensureObjective(MinecraftServer server) {
        runCommand(server, "scoreboard objectives add " + OBJECTIVE + " dummy " + OBJECTIVE_TITLE);
    }

    public void showSidebar(MinecraftServer server, boolean show) {
        runCommand(server, show
                ? "scoreboard objectives setdisplay sidebar " + OBJECTIVE
                : "scoreboard objectives setdisplay sidebar");
    }

    public void setScore(MinecraftServer server, String playerName, long value) {
        String command = "scoreboard players set \"" + playerName + "\" " + OBJECTIVE + " " + value;
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.execute(command, server.createCommandSourceStack());
        } catch (Exception first) {
            // 目标被管理员删掉了：重建后重试一次。
            try {
                ensureObjective(server);
                dispatcher.execute(command, server.createCommandSourceStack());
            } catch (Exception second) {
                LOGGER.debug("[MineBoard] 写入计分板失败: {}", command, second);
            }
        }
    }

    public void resetScore(MinecraftServer server, String playerName) {
        runCommand(server, "scoreboard players reset \"" + playerName + "\" " + OBJECTIVE);
    }

    public void runCommand(MinecraftServer server, String command) {
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.execute(command, server.createCommandSourceStack());
        } catch (Exception e) {
            LOGGER.debug("[MineBoard] 指令执行失败: {}", command, e);
        }
    }
}
