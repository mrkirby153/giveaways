package com.mrkirby153.snowsgivingbot.web;

import lombok.Getter;
import org.springframework.security.core.userdetails.User;

import java.util.ArrayList;

@Getter
public class DiscordUser extends User {

    private final String id;
    private final String discordUsername;
    private final String discriminator;
    private final String avatar;

    public DiscordUser(String username, String discriminator, String id, String avatar) {
        super(id, "", new ArrayList<>());
        this.id = id;
        this.avatar = avatar;
        this.discriminator = discriminator;
        this.discordUsername = username;
    }
}
