package com.mrkirby153.snowsgivingbot.web;

import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import com.mrkirby153.snowsgivingbot.web.dto.WebUser;
import com.mrkirby153.snowsgivingbot.web.services.DiscordOAuthService;
import com.mrkirby153.snowsgivingbot.web.services.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
    private final JwtService jwtService;
    @Value("${oauth.client-id}")
    private String clientId;

    public LoginController(DiscordOAuthService oAuthService, JwtService jwtService) {
        this.oAuthService = oAuthService;
        this.jwtService = jwtService;
    }


    @GetMapping("/client")
    public String clientId() {
        return "\"" + clientId + "\"";
    }

    @GetMapping("/user")
    public WebUser user(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof DiscordUser)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Auth principal is not a discord user");
        }
        DiscordUser user = (DiscordUser) principal;
        return new WebUser(user.getId(), user.getUsername(), user.getDiscriminator(),
            user.getAvatar());
    }


    @PostMapping("/login")
    public CompletableFuture<String> authorizeCode(
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
                    DiscordOAuthUser user = oAuthService.getDiscordUser(c).get();
                    return jwtService.generateToken(user);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }
}
