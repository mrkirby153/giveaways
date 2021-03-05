package com.mrkirby153.snowsgivingbot.event;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import lombok.Data;
import net.dv8tion.jda.api.entities.User;

@Data
public class GiveawayEnterEvent {

    private final User user;
    private final GiveawayEntity giveaway;
}
