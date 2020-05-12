package com.mrkirby153.snowsgivingbot.web.controller;

import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import lombok.NonNull;
import org.springframework.security.core.Authentication;

public class HttpUtils {


    public static DiscordUser getUser(@NonNull Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if(!(principal instanceof DiscordUser)) {
            throw new IllegalArgumentException("The principal was not a user");
        }
        return (DiscordUser) principal;
    }
}
