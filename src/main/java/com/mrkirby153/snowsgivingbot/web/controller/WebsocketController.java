package com.mrkirby153.snowsgivingbot.web.controller;

import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class WebsocketController {

    private final SimpMessagingTemplate template;
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

    @MessageMapping("/me")
    @SendTo("/topic/me")
    public DiscordOAuthUser getUser(Authentication authentication) {
        DiscordUser user = HttpUtils.getUser(authentication);
        return new DiscordOAuthUser(user.getId(), user.getUsername(), user.getDiscriminator(),
            user.getAvatar());
    }

    @MessageMapping("/send")
    public void specificUserSend(@Payload String user) {
        log.info("Sending to specific user " + user);
        template.convertAndSendToUser(user, "/queue/testing", "Ping!");
    }

    @Data
    @AllArgsConstructor
    public static class WebsocketEndpointResult {

        private boolean enabled;
        private String wsUrl;
    }
}
