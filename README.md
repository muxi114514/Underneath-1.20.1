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

