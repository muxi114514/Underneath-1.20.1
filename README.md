# Underneath

A native Forge **1.20.1** recreation of the **Underneath** dimension from **RLCraft Dregora** (originally a 1.12.2 OpenTerrainGenerator preset by NLBlackEagle & Shivaxi).

The original dimension only existed as OTG preset data, and OTG was never updated past 1.12/1.16 — so this mod rebuilds the whole thing natively: the terrain math, the structure engine, and all of the original building data are ported to run on modern Forge without OTG.

> ⚠️ This mod is built **specifically for the 1.20.1 RLCraft modpack environment**. It expects the pack's mods to be present (EPCA, Lycanites Mobs, BetterNether, Quark, Defiled Lands, Varied Commodities 1.20.1, Spartan Weaponry family, and more) and will not work standalone.

## What's inside

**A hostile underground world.** Eternal night, no skylight, a dark red haze. The terrain is an "inverted world": a floor layer at the bottom, a mid-plate, a massive 130-block-tall main cavern, and a solid dome ceiling above it all — a faithful port of the original OTG terrain formula, seams and stalactites included.

**A blood ocean.** The dimension's water is blood (sea level Y63). It behaves like water — you can swim in it, drown in it, and fish swim in it — but touching real water makes it congeal into flesh blocks. Blood springs weep from cavern walls; blood lakes pool in the depths.

**Underground megacities.** Sprawling ruined cities generate across the main cavern: highway grids with proper crossroads, skyscrapers packed along the streets, treasure vaults, maintenance ducts hanging from the dome, collapsed ruins, and floors seeded with parasite spawners and loot chests. The city layout uses the original OTG plotting algorithm, so cities grow organically and never overlap.

**21 biomes.** The core biomes (Underneath, Corrupted, Defiled Caverns, Underforest, Underspikes and their edge/pillar variants) plus the Ancient Remnants region — a giant derelict structure zone placed exactly where the original preset's map image put it, crystal plating and all.

**Parasites.** The dimension is infested. EPCA parasites spawn naturally throughout (regardless of light level), fins swim in the blood ocean, and city spawners cycle through a 30-mob pool. All of it is configurable.

**Loot.** Over 400 loot tables ported from the original preset, including the full RLCraft weapon bundle system — chests can drop gear from across the whole modpack, plus a set of unique named weapons with custom enchantments and lore.

## Getting in (and out)

1. Craft a **Bloodfire Flint and Steel**: an **Echo Shard** + a **Cincinnasite** (BetterNether). It strikes blood-red flames.
2. Find an **Ancient City** and light the massive reinforced deepslate gate at its center. Step through.
3. You arrive on top of the dome, next to an indestructible anchor portal (your way back). Find a hole and descend — fall damage is greatly reduced here, so jumping off the dome into the cavern is survivable.
4. To leave from the depths: find a **ruined portal** in the bottom caverns (two ashenstone pillars with a broken lintel), patch the frame with ashenstone, and light it. It drops you back on the overworld surface and leaves a portal behind.

## Configuration

Everything spawn/loot related can be tuned in `config/underneath/`:

- `spawner_pool.json` — the 30-mob city spawner pool (id → weight) and natural spawning caps/intervals
- `entity_map.json` / `item_map.json` / `loot_map.json` — override any id mapping used during structure generation
- `loot_tables/` — drop a table JSON here (same path as the built-in one) to override it entirely

## Credits

- **NLBlackEagle & Shivaxi** — the original Underneath preset (RLCraft Dregora)
- **OpenTerrainGenerator** — the structure and terrain semantics this port is faithful to

License: **All Rights Reserved** (see LICENSE for third-party content notes).

---

# 中文说明

**Underneath（深渊）** 是对 **RLCraft Dregora** 整合包中 1.12.2 OTG 预设维度「Underneath」的原生 **Forge 1.20.1** 重建（原作者 NLBlackEagle & Shivaxi）。

原维度只以 OTG 预设数据的形式存在，而 OTG 从未更新到 1.12/1.16 之后——所以本模组把整套东西原生重建了：地形公式、结构引擎、全部原版建筑数据，都移植到现代 Forge 上运行，不再需要 OTG。

> ⚠️ 本模组**专为 1.20.1 RLCraft 整合包环境打造**，依赖包内的众多 mod（归寄万物 EPCA、恐怖生物、更好的下界、夸克、污秽之地、Varied Commodities 1.20.1、斯巴达武器系列等），不能独立运行。

## 内容概览

**一个充满敌意的地下世界。** 永夜、无天光、暗红色的浓雾。地形是一个"倒置世界"：底部地板层、中层板、高达 130 格的巨型主空腔、以及封顶的实心穹顶——忠实移植原版 OTG 地形公式，连棱面和石笋都原样保留。

**血之海。** 这个维度的"水"是血（海平面 Y63）。它的行为和水一样——可以游泳、会溺水、鱼在其中游动——但接触真正的水时会凝结成血肉块。洞壁上渗出血泉，深处积着血湖。

**地下巨城。** 主空腔中生成绵延的废弃城市：带完整十字路口的公路网、沿街密布的摩天楼、藏宝库、悬挂在穹顶下的维修管道、坍塌的废墟，楼板间散布着寄生虫刷怪笼和战利品箱。城市布局使用原版 OTG 布点算法，生长自然、永不重叠。

**21 个群系。** 核心群系（地渊、腐化之地、污秽洞窟、地底森林、地底尖刺及其边缘/石柱变体）加上远古遗迹区——一片巨型废弃结构地带，位置完全按原预设的地图图像摆放，水晶板铺装一应俱全。

**寄生虫。** 这个维度已被寄生。EPCA 寄生虫在全维度自然刷新（无视亮度），鳍鱼在血海中游弋，城市刷怪笼从 30 种怪物池中轮换生成。以上全部可配置。

**战利品。** 从原预设移植了 400 多张战利品表，包括完整的 RLCraft 武器捆包系统——宝箱可能开出整个整合包的装备，还有一批带自定义附魔和背景故事的具名传奇武器。

## 进入与离开

1. 合成**血焰打火石**：**回响碎片** + **辛西纳石**（更好的下界）。它打出的是血红色的火焰。
2. 找到一座**远古城市**，点燃其中央那扇巨大的强化深板岩之门，走进去。
3. 你会到达穹顶之上，身旁是一扇不可摧毁的锚点传送门（回程用）。找个洞往下跳——这里的摔落伤害大幅降低，从穹顶跃入主空腔摔不死。
4. 从深处离开：在底层洞穴找到**废弃传送门**（两根灰烬石柱、断裂的门楣），用灰烬石补全门框并点燃。它会把你送回主世界地表，并在那里留下一扇门。

## 配置

刷怪与战利品相关的一切都可以在 `config/underneath/` 中调整：

- `spawner_pool.json` —— 城市刷怪笼的 30 怪候选池（id → 权重）及自然刷怪的上限/间隔
- `entity_map.json` / `item_map.json` / `loot_map.json` —— 覆盖结构生成时使用的任意 id 映射
- `loot_tables/` —— 把表 JSON 放到这里（与内置表同路径）即可整表覆盖

## 致谢

- **NLBlackEagle & Shivaxi** —— Underneath 原预设（RLCraft Dregora）
- **OpenTerrainGenerator** —— 本移植所忠实的结构与地形语义

协议：**保留所有权利**（第三方内容说明见 LICENSE）。
