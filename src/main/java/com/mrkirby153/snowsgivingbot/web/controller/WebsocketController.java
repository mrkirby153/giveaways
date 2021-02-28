package com.mrkirby153.snowsgivingbot.web.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Slf4j
public class WebsocketController {

    @Value("${ws.endpoint:}")
    private String wsEndpoint;

    @GetMapping("/ws")
    public WebsocketEndpointResult getWebsocketEndpoint() {
        if (wsEndpoint.isBlank() || wsEndpoint.isEmpty()) {
            return new WebsocketEndpointResult(false, null);
        } else {
            return new WebsocketEndpointResult(true, wsEndpoint);
        }
    }

    @MessageMapping("/ping")
    @SendTo("/topic/ping")
    public String ping() {
        log.info("Received ping from websocket!");
        return "Pong!";
    }

    @Data
    @AllArgsConstructor
    public static class WebsocketEndpointResult {

        private boolean enabled;
        private String wsUrl;
    }
}
