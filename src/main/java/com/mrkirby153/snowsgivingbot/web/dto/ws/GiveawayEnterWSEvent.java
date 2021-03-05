package com.mrkirby153.snowsgivingbot.web.dto.ws;

import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import lombok.Data;

@Data
public class GiveawayEnterWSEvent {

    private final String userId;
    private final GiveawayDto giveaway;
}
