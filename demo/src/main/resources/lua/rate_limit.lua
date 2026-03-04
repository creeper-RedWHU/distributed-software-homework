-- rate_limit.lua
-- 滑动窗口限流脚本
--
-- KEYS[1] = rate:limit:{userId}
-- ARGV[1] = 窗口起始时间戳 (windowStart)
-- ARGV[2] = 最大请求数 (maxRequests)
-- ARGV[3] = 当前时间戳 (now)
-- ARGV[4] = Key过期时间秒数 (ttl)
--
-- 返回值:
--   0  未超限（已添加记录）
--   1  已超限（请求被拒绝）

-- 清除窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])

-- 统计当前窗口内的请求数
local count = redis.call('ZCARD', KEYS[1])

if count < tonumber(ARGV[2]) then
    -- 未超限：添加当前请求，设置过期
    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3])
    redis.call('EXPIRE', KEYS[1], ARGV[4])
    return 0
end

-- 已超限
return 1
