package com.mrkirby153.snowsgivingbot.web.controller;

import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import com.mrkirby153.snowsgivingbot.web.services.WebGiveawayService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api")
@AllArgsConstructor
public class GiveawayController {

    private final WebGiveawayService giveawayService;

    @GetMapping("/giveaways/{server}")
    public List<GiveawayDto> getAllGiveaways(@PathVariable(name = "server") String guild,
        Authentication authentication) {
        DiscordUser user = HttpUtils.getUser(authentication);
        return giveawayService.getGiveaways(guild, user);
    }
}
