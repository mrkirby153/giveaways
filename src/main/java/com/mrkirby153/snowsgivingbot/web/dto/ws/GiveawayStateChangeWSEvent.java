package com.mrkirby153.snowsgivingbot.web.dto.ws;

import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import lombok.Data;

@Data
public class GiveawayStateChangeWSEvent {

    private final State state;
    private final GiveawayDto giveaway;

    public enum State {
        START,
        END
    }
}
