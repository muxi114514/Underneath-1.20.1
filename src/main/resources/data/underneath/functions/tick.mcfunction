# 每 tick 重新 enable（trigger 触发一次后自动禁用，原版防刷机制）
scoreboard players enable @a explosion
# 咒文结算顺序：正确音节(63)先清零 → 后两行的选择器不再匹配她
execute as @a[scores={explosion=63}] run function underneath:explosion_success
execute as @a[scores={explosion=1}] run function underneath:explosion_hint
execute as @a[scores={explosion=2..}] run function underneath:explosion_wrong
execute as @a[scores={explosion=..-1}] run function underneath:explosion_wrong
