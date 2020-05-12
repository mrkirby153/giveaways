package com.mrkirby153.snowsgivingbot.web;

import com.mrkirby153.snowsgivingbot.web.dto.DiscordOAuthUser;
import lombok.Getter;
import org.springframework.security.core.userdetails.User;

import java.util.ArrayList;

@Getter
public class DiscordUser extends User {

    private final String id;
    private final String discriminator;
    private final String avatar;

    public DiscordUser(DiscordOAuthUser user) {
        super(user.getUsername(), "", new ArrayList<>());
        this.id = user.getId();
        this.avatar = user.getAvatar();
        this.discriminator = user.getDiscriminator();
    }

    public DiscordUser(String username, String discriminator, String id, String avatar) {
        super(username, "", new ArrayList<>());
        this.id = id;
        this.avatar = avatar;
        this.discriminator = discriminator;
    }
}
