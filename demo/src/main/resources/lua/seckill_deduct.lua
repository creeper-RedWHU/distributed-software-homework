-- seckill_deduct.lua
-- 秒杀库存原子扣减脚本
--
-- KEYS[1] = seckill:stock:{activityId}   库存计数器
-- KEYS[2] = seckill:bought:{activityId}:{userId}  用户已购数量
-- ARGV[1] = 限购数量 (limitPerUser)
-- ARGV[2] = 购买数量 (quantity)
--
-- 返回值:
--   >= 0  扣减成功，返回剩余库存
--   -1    库存不足
--   -2    超出限购

-- Step 1: 检查库存
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < tonumber(ARGV[2]) then
    return -1
end

-- Step 2: 检查用户限购
local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
if bought + tonumber(ARGV[2]) > tonumber(ARGV[1]) then
    return -2
end

-- Step 3: 原子扣减库存 + 增加用户购买数
redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('INCRBY', KEYS[2], ARGV[2])

-- Step 4: 返回剩余库存
return tonumber(redis.call('GET', KEYS[1]))
