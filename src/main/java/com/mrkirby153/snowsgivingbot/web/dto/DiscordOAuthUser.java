package com.mrkirby153.snowsgivingbot.web.dto;

import lombok.Data;
import lombok.NonNull;

@Data
public class DiscordOAuthUser {

    @NonNull
    private final String id;
    @NonNull
    private final String username;
    @NonNull
    private final String discriminator;

    private final String avatar;
}
