# 爆裂魔法彩蛋 trigger（load 时建目标；已存在时本行静默失败无害）
# 玩家动线：/trigger 按 Tab 发现 explosion → 裸触发得谜语（血海之面=Y63）→ /trigger explosion set 63 → 武器全家桶
scoreboard objectives add explosion trigger {"text":"EXPLOSION","color":"dark_red","bold":true}
