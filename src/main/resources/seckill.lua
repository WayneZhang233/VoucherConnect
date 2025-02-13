-- check if succeed
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- check
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- out of stock
    return 1
end
if(redis.call('sismember', orderKey, userId) == 1) then
    -- duplicate orders from the same user
    return 2
end

-- operations
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- send a message to the queue  XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders','*', 'userId', userId, 'voucherId', voucherId, 'id', orderId);
return 0
