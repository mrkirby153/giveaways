package com.mrkirby153.snowsgivingbot.web.controller;

import com.mrkirby153.snowsgivingbot.commands.HelpCommand;
import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import com.mrkirby153.snowsgivingbot.web.dto.WebUser;
import com.mrkirby153.snowsgivingbot.web.services.DiscordOAuthService;
import com.mrkirby153.snowsgivingbot.web.services.JwtService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api")
@Slf4j
public class LoginController {

    private final DiscordOAuthService oAuthService;
    private final JwtService jwtService;
    @Value("${oauth.client-id}")
    private String clientId;
    @Value("${bot.permissions:" + HelpCommand.DEFAULT_PERMISSIONS + "}")
    private String permissions;
    private final ShardManager shardManager;

    public LoginController(DiscordOAuthService oAuthService, JwtService jwtService,
        ShardManager shardManager) {
        this.oAuthService = oAuthService;
        this.jwtService = jwtService;
        this.shardManager = shardManager;
    }


    @GetMapping(value = "/client", produces = {MediaType.TEXT_PLAIN_VALUE})
    public String clientId() {
        return "\"" + clientId + "\"";
    }

    @GetMapping("/user")
    public WebUser user(Authentication authentication) {
        DiscordUser user = HttpUtils.getUser(authentication);
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
            }).exceptionally(ex -> {
                if (ex instanceof CompletionException) {
                    Throwable cause = ex.getCause();
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cause.getMessage());
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            });
    }

    @GetMapping(value = "/invite", produces = {MediaType.TEXT_PLAIN_VALUE})
    public String invite(HttpServletResponse response) {
        JDA shard = shardManager.getShardById(0);
        if (shard == null) {
            throw new IllegalArgumentException("Could not redirect. Shard 0 not found");
        }
        return "\"" + String
            .format(HelpCommand.DISCORD_OAUTH_INVITE, shard.getSelfUser().getId(), permissions)
            + "\"";
    }
}
