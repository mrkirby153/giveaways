package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.services.AdminLoggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminLoggerManager implements AdminLoggerService {

    private final ShardManager shardManager;

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    @Value("${bot.admin-log-channel:}")
    private String logChannelId;

    @Override
    public void log(String message) {
        if (logChannelId.isEmpty()) {
            return; // Log channel is not configured
        }
        TextChannel chan = shardManager.getTextChannelById(logChannelId);
        if (chan == null) {
            log.warn("Could not send \"{}\" to log channel. Channel {} was not found", message,
                logChannelId);
            return;
        }
        if (!chan.canTalk()) {
            log.warn("Attempted to send \"{}\" to log channel {} but cannot talk", message, chan);
        }
        chan.sendMessageFormat("[`%s`] %s", sdf.format(Date.from(Instant.now())), message).queue();
    }

    @EventListener
    public void onGuildJoin(GuildJoinEvent event) {
        event.getGuild().retrieveOwner().queue(member -> {
            log(String
                .format("Joined guild %s (`%s`) owned by %s (`%s`)", event.getGuild().getName(),
                    event.getGuild().getId(), member.getUser().getName(),
                    event.getGuild().getOwnerId()));
        }, t -> log(String.format("Joined guild %s (`%s`) owned by %s", event.getGuild().getName(),
            event.getGuild().getId(), event.getGuild().getOwnerId())));
    }

    @EventListener
    public void onGuildLeave(GuildLeaveEvent event) {
        log(String
            .format("Left guild %s (`%s`)", event.getGuild().getName(), event.getGuild().getId()));
    }

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        log("Bot startup complete!");
    }
}
