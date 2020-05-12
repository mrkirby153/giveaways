package com.mrkirby153.snowsgivingbot.web;

import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import com.mrkirby153.snowsgivingbot.web.services.DiscordOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
@Slf4j
public class LoginController {

    private final DiscordOAuthService oAuthService;
    @Value("${oauth.client-id}")
    private String clientId;

    public LoginController(DiscordOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }


    @GetMapping("/client")
    public String clientId() {
        return "\"" + clientId + "\"";
    }

    @PostMapping("/login")
    public CompletableFuture<DiscordOAuthUser> authorizeCode(
        @RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing code");
        }
        if (!payload.containsKey("code")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing code");
        }

        // Should return a JWT
        String code = payload.get("code").toString();
        String redirectUri = payload.get("redirect_uri").toString();

        return oAuthService
            .getAuthorizationToken(redirectUri, code).thenApplyAsync(c -> {
                try {
                    return oAuthService.getDiscordUser(c).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }
}
