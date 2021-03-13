package com.mrkirby153.snowsgivingbot.web.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayEnterEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import com.mrkirby153.snowsgivingbot.web.dto.ws.GiveawayEnterWSEvent;
import com.mrkirby153.snowsgivingbot.web.dto.ws.GiveawayStateChangeWSEvent;
import com.mrkirby153.snowsgivingbot.web.dto.ws.GiveawayStateChangeWSEvent.State;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class GiveawayWebsocketController {

    private static final String GIVEAWAY_STATE_FORMAT = "/topic/%s/giveaway";
    private static final String USER_ENTER_GIVEAWAY_TOPIC = "/queue/giveaway/%s/user";

    private final SimpMessagingTemplate messagingTemplate;
    private final ShardManager shardManager;

    @EventListener
    public void onGiveawayStart(GiveawayStartedEvent event) {
        GiveawayEntity giveaway = event.getGiveaway();
        log.debug("Publishing {} start to websocket", giveaway);
        String topic = String.format(GIVEAWAY_STATE_FORMAT, giveaway.getGuildId());
        messagingTemplate
            .convertAndSend(topic,
                new GiveawayStateChangeWSEvent(State.START,
                    new GiveawayDto(event.getGiveaway(), getChannelName(event.getGiveaway()),
                        false)));
    }

    @EventListener
    public void onGiveawayEnd(GiveawayEndedEvent event) {
        GiveawayEntity giveaway = event.getGiveaway();
        log.debug("Publishing {} end to websocket", giveaway);
        String topic = String.format(GIVEAWAY_STATE_FORMAT, giveaway.getGuildId());
        messagingTemplate
            .convertAndSend(topic, new GiveawayStateChangeWSEvent(State.END,
                new GiveawayDto(event.getGiveaway(), getChannelName(event.getGiveaway()), false)));
    }

    @EventListener
    public void onGiveawayEnter(GiveawayEnterEvent event) {
        User u = event.getUser();
        GiveawayEntity g = event.getGiveaway();
        log.debug("Sending enter of {} to giveaway {}", u, g);
        messagingTemplate.convertAndSendToUser(u.getId(), String.format(USER_ENTER_GIVEAWAY_TOPIC, g.getGuildId()),
            new GiveawayEnterWSEvent(u.getId(),
                new GiveawayDto(event.getGiveaway(), getChannelName(event.getGiveaway()), true)));
    }

    private String getChannelName(GiveawayEntity entity) {
        TextChannel channel = shardManager.getTextChannelById(entity.getChannelId());
        if (channel == null) {
            return "unknown-channel";
        } else {
            return channel.getName();
        }
    }
}
