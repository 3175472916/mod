package dev.mineboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 配置文件：{@code config/mineboard/config.json}。首次启动会自动生成。
 */
public final class MineBoardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 创造模式是否计数（默认 false，防止刷分）。 */
    public boolean countCreative = false;
    /** 旁观模式是否计数（默认 false）。 */
    public boolean countSpectator = false;
    /** 开服时自动把计分板挂到侧边栏。 */
    public boolean showSidebarOnStart = true;
    /** 同步计分板的间隔（tick），20 = 1 秒，最小 1。 */
    public int updateIntervalTicks = 20;
    /** 不参与计数的方块，可写 {@code minecraft:stone} 或 {@code stone}。 */
    public List<String> ignoredBlocks = new ArrayList<>();

    public static MineBoardConfig load() {
        Path file = file();
        MineBoardConfig config = new MineBoardConfig();
        if (Files.exists(file)) {
            try {
                MineBoardConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MineBoardConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                MineBoard.LOGGER.warn("[MineBoard] 读取 {} 失败，使用默认配置", file, e);
            }
        }
        config.normalize();
        config.save();
        return config;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(MineBoard.MOD_ID)
                .resolve("config.json");
    }

    private void normalize() {
        if (ignoredBlocks == null) {
            ignoredBlocks = new ArrayList<>();
        }
        updateIntervalTicks = Math.max(1, Math.min(updateIntervalTicks, 20 * 60));
    }

    public void save() {
        try {
            Path file = file();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineBoard.LOGGER.warn("[MineBoard] 保存配置失败", e);
        }
    }

    public boolean isIgnored(String blockId) {
        String id = normalizeId(blockId);
        for (String ignored : ignoredBlocks) {
            if (normalizeId(ignored).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }
}
