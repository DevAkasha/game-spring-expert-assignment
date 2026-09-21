package com.gameexpert.ws.handler;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class MoveWsHandler implements WsMessageHandler {
    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "move";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        String finalSceneActionId = WsFields.optionalFinalSceneActionId(message);
        engineManager.enqueue(context.worldId(), new PlayerAction.Move(
                context.nickname(),
                WsFields.finiteNumber(message, "x"),
                WsFields.finiteNumber(message, "y"),
                WsFields.finiteNumber(message, "z"),
                WsFields.finiteFloat(message, "yaw"),
                WsFields.finiteFloat(message, "pitch"),
                WsFields.booleanValue(message, "crouching"),
                WsFields.booleanValue(message, "gliding"),
                finalSceneActionId));
    }
}
