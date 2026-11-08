package dev.mineboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 挖掘数据存储。结构：UUID -> { name, total, blocks: { 方块ID: 次数 } }，
 * 以 JSON 形式保存在 {@code config/mineboard/data.json}。
 */
public final class MineBoardStats {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();

    private final Path file;
    private final Map<String, Entry> players = new HashMap<>();

    public MineBoardStats(Path file) {
        this.file = file;
    }

    public static MineBoardStats load() {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve(MineBoard.MOD_ID)
                .resolve("data.json");
        MineBoardStats stats = new MineBoardStats(file);
        if (!Files.exists(file)) {
            return stats;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Entry> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded != null) {
                stats.players.putAll(loaded);
            }
        } catch (Exception e) {
            MineBoard.LOGGER.warn("[MineBoard] 读取 {} 失败，将从空数据开始", file, e);
        }
        return stats;
    }

    public void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(players, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineBoard.LOGGER.warn("[MineBoard] 保存 {} 失败", file, e);
        }
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    public void record(String uuid, String name, String blockId) {
        Entry entry = players.computeIfAbsent(uuid, key -> new Entry());
        entry.name = name;
        entry.total++;
        entry.blocks.merge(blockId, 1L, Long::sum);
    }

    /** 返回被删除的记录，没有则返回 null。 */
    public Entry remove(String uuid) {
        return players.remove(uuid);
    }

    /** 按名字（不区分大小写）删除，返回被删除的记录。 */
    public Entry removeByName(String name) {
        String key = findKeyByName(name);
        return key == null ? null : players.remove(key);
    }

    public void clear() {
        players.clear();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public int size() {
        return players.size();
    }

    public long total(String uuid) {
        Entry entry = players.get(uuid);
        return entry == null ? 0L : entry.total;
    }

    public Entry findByName(String name) {
        String key = findKeyByName(name);
        return key == null ? null : players.get(key);
    }

    public String findKeyByName(String name) {
        if (name == null) {
            return null;
        }
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Entry> entry : players.entrySet()) {
            if (entry.getValue().name != null
                    && entry.getValue().name.toLowerCase(Locale.ROOT).equals(needle)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 全部玩家，按总分从高到低。 */
    public List<Entry> ranking() {
        List<Entry> list = new ArrayList<>(players.values());
        list.sort(Comparator.comparingLong((Entry entry) -> entry.total).reversed());
        return list;
    }

    /** 某个玩家挖得最多的方块。 */
    public static final class Entry {
        public String name = "";
        public long total;
        public Map<String, Long> blocks = new HashMap<>();

        public List<Map.Entry<String, Long>> topBlocks(int limit) {
            List<Map.Entry<String, Long>> list = new ArrayList<>(blocks.entrySet());
            list.sort(Map.Entry.<String, Long>comparingByValue().reversed());
            return list.size() <= limit ? list : list.subList(0, limit);
        }

        public int rankIn(List<Entry> ranking) {
            for (int i = 0; i < ranking.size(); i++) {
                if (ranking.get(i) == this) {
                    return i + 1;
                }
            }
            return -1;
        }
    }
}
