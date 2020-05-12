package com.mrkirby153.snowsgivingbot.web.dto;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
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
}
