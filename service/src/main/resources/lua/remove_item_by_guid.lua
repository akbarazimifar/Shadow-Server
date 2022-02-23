-- keys: queue_key, queue_metadata_key, queue_index
-- argv: guid_to_remove

local removedMessages = {}

for _, guid in ipairs(ARGV) do
    local messageId = redis.call("HGET", KEYS[2], guid)

    if messageId then
        local envelope = redis.call("ZRANGEBYSCORE", KEYS[1], messageId, messageId, "LIMIT", 0, 1)
        local sender   = redis.call("HGET", KEYS[2], messageId)

        redis.call("ZREMRANGEBYSCORE", KEYS[1], messageId, messageId)
        redis.call("HDEL", KEYS[2], guid)
        redis.call("HDEL", KEYS[2], messageId .. "guid")

        if sender then
            redis.call("HDEL", KEYS[2], sender)
            redis.call("HDEL", KEYS[2], messageId)
        end

        if (redis.call("ZCARD", KEYS[1]) == 0) then
            redis.call("ZREM", KEYS[3], KEYS[1])
        end

        if envelope and next(envelope) then
            removedMessages[#removedMessages + 1] = envelope[1]
        end
    end
end

return removedMessages