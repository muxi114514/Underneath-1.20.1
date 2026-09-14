# 裸 /trigger explosion（默认 set 1）：深渊低语给出谜语（与念错分支共用文言版）
tellraw @s {"text":"深渊深处传来低语——","color":"dark_gray","italic":true}
tellraw @s {"text":"『咏者，咒遗末响。沧溟接霄之处，即此音之数。』","color":"dark_red","italic":true}
playsound minecraft:ambient.cave block @s ~ ~ ~ 1 0.5
scoreboard players set @s explosion 0
