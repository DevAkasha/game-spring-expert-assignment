package com.gameexpert.chat.relay;

import java.util.Map;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.gameexpert.chat.service.LocalChatSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChatRelay implements MessageListener {
    public static final String CHANNEL = "webcraft:chat";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LocalChatSender localChatSender;

    public void publish(Long worldId, Object message) {
        Map<String, Object> envelope = Map.of("worldId", worldId, "message", message);
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        JsonNode envelope = objectMapper.readTree(message.getBody());
        Long worldId = envelope.path("worldId").asLong();
        JsonNode payload = envelope.path("message");
        localChatSender.send(worldId, payload);
    }
}
