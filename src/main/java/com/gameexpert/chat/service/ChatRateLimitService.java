package com.gameexpert.chat.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatRateLimitService {

    private static final RedisScript<Long> ALLOW_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current >= 5 then
                return 0
            end
            local updated = redis.call('INCR', KEYS[1])
            if updated == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean allow(Long playerId) {
        String key = "chat:limit:" + playerId;
        Long allowed = redisTemplate.execute(ALLOW_SCRIPT, List.of(key), "10");
        return allowed != null && allowed == 1L;
    }
}
