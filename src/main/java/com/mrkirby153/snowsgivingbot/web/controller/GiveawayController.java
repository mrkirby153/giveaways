package com.mrkirby153.snowsgivingbot.web.controller;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import com.mrkirby153.snowsgivingbot.web.dto.ServerDto;
import com.mrkirby153.snowsgivingbot.web.services.WebGiveawayService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
@RequestMapping("/api")
@AllArgsConstructor
public class GiveawayController {

    private final WebGiveawayService giveawayService;
    private final JDA jda;

    private final LoadingCache<String, ServerDto> dtoCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES).build(
            new CacheLoader<String, ServerDto>() {
                @Override
                public ServerDto load(String key) throws Exception {
                    Guild g = jda.getGuildById(key);
                    if (g == null) {
                        throw new IllegalStateException("Guild not found");
                    }
                    return new ServerDto(g.getId(), g.getName(), g.getIconId());
                }
            });

    @GetMapping("/giveaways/{server}")
    public List<GiveawayDto> getAllGiveaways(@PathVariable(name = "server") String guild,
        Authentication authentication) {
        DiscordUser user = HttpUtils.getUser(authentication);
        return giveawayService.getGiveaways(guild, user);
    }

    @GetMapping("/server/{server}")
    public ServerDto getServer(@PathVariable(name = "server") String guild)
        throws ExecutionException {
        Guild g = jda.getGuildById(guild);
        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return dtoCache.get(g.getId());
    }
}
