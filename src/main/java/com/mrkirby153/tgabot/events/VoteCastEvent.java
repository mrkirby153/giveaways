package com.mrkirby153.tgabot.events;

import com.mrkirby153.tgabot.entity.Vote;
import lombok.Data;
import net.dv8tion.jda.api.entities.User;

/**
 * Event fired when a vote is cast
 */
@Data
public class VoteCastEvent {

    private final User user;
    private final Vote vote;
}
