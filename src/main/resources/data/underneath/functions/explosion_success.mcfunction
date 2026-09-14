# EXPLOSION!! 咒文完整：爆炸演出 + 全家桶必出表（3 捆包+21 具名武器，含惠惠法杖）
tellraw @s {"text":"※ EXPLOSION!! ※","color":"red","bold":true}
tellraw @s {"text":"爆裂魔法响彻深渊，远古的兵装随烟尘散落于你的行囊。","color":"dark_red"}
playsound minecraft:entity.generic.explode player @s ~ ~ ~ 1 0.8
execute at @s run particle minecraft:explosion_emitter ~ ~1 ~ 0 0 0 0 1
loot give @s loot underneath:loot/unique_loot_epic/all_unique_weapons
scoreboard players set @s explosion 0
