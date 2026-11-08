# MineBoard · 挖掘计分榜

面向 **Minecraft Java Edition 26.3（Wilderness Bound）+ Fabric Loader 0.19.5** 的服务端模组：
统计每名玩家挖掉的方块总数，写入原版计分板目标 `mineboard`，并让侧边栏直接变成实时排行榜。

---

## 1. 为什么是模组，而不是数据包

原版计分板的统计准则只有**按方块种类**的一条：

```mcfunction
scoreboard objectives add stone_mined minecraft.mined:minecraft.stone
```

也就是说 `minecraft.mined:*` 只能一个一个方块建目标，**没有“所有方块总量”这个准则**，而数据包既不能遍历计分板目标求和（几百个目标的逐 tick 累加会直接拖垮服务器），也没有“玩家破坏方块”的通用进度触发器。所以“总挖掘数”只能由模组在方块被破坏的那一刻累加。

本模组只做三件事，全部走原版机制，因此**纯服务端、不需要客户端安装**：

| 步骤 | 实现 |
| --- | --- |
| 捕获挖掘 | Fabric API 的 `PlayerBlockBreakEvents.AFTER` |
| 累计与持久化 | 自己维护 JSON（`config/mineboard/data.json`），含每个玩家挖过的方块明细 |
| 展示 | 用原版 `/scoreboard` 指令把总分写进 `mineboard` 目标，侧边栏由原版自动按分数降序排列 |

> 侧边栏排序是原版 `scoreboard` 自带的行为（按分数从高到低），所以排行榜不需要自己画 GUI，离线玩家的分数也会一直留在榜上。

---

## 2. 版本事实（已核对）

| 项 | 值 |
| --- | --- |
| Minecraft | 26.3（2026-09-15 发布，data pack format 121.0，协议 777） |
| 最低 Java | **Java SE 25** |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.165.2+26.3（最低要求写的是 0.165.2） |
| Loom / Gradle | Loom 1.17.21（插件 id `net.fabricmc.fabric-loom`）/ Gradle 9.6.0 |
| 混淆映射 | 26.x **不再混淆**，无需 Yarn / Mojang mappings，代码直接用游戏内真实类名 |

参考资料：
- [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html)
- [Java Edition 26.3 – Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_26.3)
- [Fabric 文档：Migrating Mappings（26.x 不再需要 mappings）](https://docs.fabricmc.net/develop/porting/mappings/)
- [Fabric API 0.165.2+26.3 Javadoc](https://maven.fabricmc.net/docs/fabric-api-0.165.2+26.3/)

---

## 3. 目录结构

```
mineboard/
├─ build.gradle                 # Loom 1.17，Java 25，无 mappings
├─ settings.gradle
├─ gradle.properties            # 版本号集中在这里
├─ gradle/wrapper/gradle-wrapper.properties
└─ src/main/
   ├─ java/dev/mineboard/
   │  ├─ MineBoard.java         # 入口：事件注册、计分、计分板同步
   │  ├─ MineBoardStats.java    # 数据模型 + JSON 读写
   │  ├─ MineBoardConfig.java   # 配置读写
   │  ├─ MineBoardCommands.java # /mineboard 指令树
   │  └─ Bridge.java            # 唯一与版本强相关的调用集中处
   └─ resources/fabric.mod.json
```

---

## 4. 构建

> 需要 **JDK 25**。`gradle.properties` 里的 toolchain 会要求 Gradle 用 25 编译。

### 方式 A：IDEA（推荐）

1. 用 IntelliJ IDEA 打开 `mineboard/` 目录，信任项目；
2. IDEA 会读取 `gradle/wrapper/gradle-wrapper.properties` 自动下载 Gradle 9.6；
3. 右侧 Gradle 面板执行 `Tasks → build → build`；
4. 产物：`build/libs/mineboard-1.0.0.jar`。

### 方式 B：命令行

```bash
cd mineboard
gradle wrapper --gradle-version 9.6.0   # 首次生成 gradlew / gradle-wrapper.jar
./gradlew build                          # Windows: gradlew.bat build
```

> 仓库里没有附带 `gradle-wrapper.jar`（二进制文件不便入库）。用 IDEA 打开、或本机已装 Gradle 时执行上面的 `gradle wrapper`，都会补齐它。

### 方式 C：借官方模板

若构建环境有问题，可到 <https://fabricmc.net/develop/template/> 生成一个 26.3 + Fabric 的项目，然后把本项目的 `src/`、`gradle.properties` 里的版本号抄过去——`build.gradle` 与官方模板结构一致。

### 方式 D：GitHub Actions（本机没装 JDK 25 时最省事）

仓库里已带 `.github/workflows/build.yml`：推到 GitHub 后 Actions 会自动用 Temurin 25 + Gradle 9.6 构建，
在 Artifacts 里下载 `mineboard-1.0.0.jar`。

---

## 5. 安装

1. 服务端安装 **Fabric Loader 0.19.5**（`fabric-server-launch.jar` 方式）；
2. `mods/` 放入：
   - `fabric-api-0.165.2+26.3.jar`（必须）
   - `mineboard-1.0.0.jar`
3. 启动服务器。控制台出现 `[MineBoard] 已加载` 即成功；
4. 进游戏后侧边栏默认显示挖掘榜（可在配置里关掉）。

客户端**不需要**装本模组；单人游戏（内置服务端）也能用。

---

## 6. 指令

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/mineboard` | 所有人 | 帮助 |
| `/mineboard top [数量]` | 所有人 | 排行榜，数量 1–50，默认 10 |
| `/mineboard me` | 所有人 | 自己的总数与名次 |
| `/mineboard blocks [玩家]` | 所有人 | 某玩家挖得最多的 10 种方块 |
| `/mineboard display sidebar` | OP（等级 2） | 把榜单挂到侧边栏 |
| `/mineboard display off` | OP（等级 2） | 关闭侧边栏 |
| `/mineboard reset all` | OP（等级 2） | 清空所有人数据并重建计分板 |
| `/mineboard reset <玩家>` | OP（等级 2） | 清空单个玩家（支持离线名） |
| `/mineboard reload` | OP（等级 2） | 重新读取 `config.json` |

计分板目标名固定为 `mineboard`，因此也可以直接用原版指令操作，例如查某人分数：

```mcfunction
scoreboard players get Steve mineboard
```

---

## 7. 配置与数据

`config/mineboard/config.json`（首次启动自动生成）：

```json
{
  "countCreative": false,
  "countSpectator": false,
  "showSidebarOnStart": true,
  "updateIntervalTicks": 20,
  "ignoredBlocks": [
    "minecraft:bedrock",
    "minecraft:barrier"
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `countCreative` | 创造模式是否计数。默认 `false`，防止刷分 |
| `countSpectator` | 旁观模式是否计数 |
| `showSidebarOnStart` | 开服自动挂侧边栏 |
| `updateIntervalTicks` | 写计分板的间隔，20 tick = 1 秒 |
| `ignoredBlocks` | 不计数的方块，`stone` 与 `minecraft:stone` 等价 |

`config/mineboard/data.json`：`UUID → { name, total, blocks }`，含每个玩家每种方块的次数。
删掉它会清空统计（计分板上的旧分数也会在下次 `reset` 或手动移除目标后消失）。

> 数据跟着**服务器实例**走（放在 config 目录），不随存档。换周目时建议 `/mineboard reset all`。

---

## 8. 实现要点

```java
PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
    // 过滤旁观 / 创造 / 空气 / 黑名单方块
    stats.record(player.getUUID().toString(), player.getName().getString(), blockId);
});
```

- 事件在**方块被成功破坏之后**触发，活塞、爆炸、自然破坏都不会误计；
- 每 1 秒把在线玩家的总分用 `scoreboard players set <名字> mineboard <总数>` 写进原版计分板；
- 写入失败（例如管理员手滑删了目标）会自动重建目标并重试一次；
- 侧边栏由原版按分数排序，离线玩家的条目不会消失，所以榜是“全服累计榜”而不是“在线榜”。

---

## 9. 防刷分建议

原版无法区分“自然生成的方块”和“玩家自己放回去的方块”，所以“挖了再放”理论上可以刷。可选做法：

1. 配置 `countCreative: false`（默认）——堵住创造模式无限刷；
2. 用领地/保护插件限制反复放置的区域；
3. 若确实需要严格模式，可在 `PlayerBlockBreakEvents.AFTER` 里再加一层“该坐标最近是否由玩家放置”的记录（需要额外监听放置事件），这属于按需扩展，本模组未内置。

---

## 10. 排错

| 现象 | 处理 |
| --- | --- |
| 启动报 `requires fabric-api` | 确认 `mods/` 里有 26.3 版 Fabric API |
| 侧边栏不显示 | `/mineboard display sidebar`；或检查是否被其他计分板插件占用 sidebar 槽位 |
| 分数不动 | 确认是生存模式（默认不统计创造）、方块不在 `ignoredBlocks` 里 |
| 编译报 `cannot find symbol` | 26.x 类名与旧 Mojang mappings 有差异（例如 `ResourceLocation` 现在叫 `Identifier`）。与版本相关的调用都集中在 `Bridge.java` 与 `MineBoard.java` 的指令封装里，按 IDE 提示改一处即可 |

---

## 11. 许可

MIT。
