package com.mrkirby153.snowsgivingbot.event;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import lombok.Data;

@Data
public class GiveawayStartedEvent {

    private final GiveawayEntity giveaway;
}
