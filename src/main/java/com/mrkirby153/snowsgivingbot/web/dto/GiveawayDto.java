package com.mrkirby153.snowsgivingbot.web.dto;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class GiveawayDto {

    private final long id;

    private final String name;
    private final String channelId;
    private final String channelName;

    private final Timestamp endsAt;
    private final boolean entered;

    private final GiveawayState state;

    public GiveawayDto(GiveawayEntity entity, String channelName, boolean entered) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.channelId = entity.getChannelId();
        this.channelName = channelName;
        this.endsAt = entity.getEndsAt();
        this.entered = entered;
        this.state = entity.getState();
    }
}
