package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.services.DiscordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class DiscordManager implements DiscordService {

    private static final Pattern jumpLinkMatcher = Pattern.compile(
        "https?://(canary\\.|ptb\\.)?discordapp.com/channels/(\\d{17,18})/(\\d{17,18})/(\\d{17,18})");
    private static final HashMap<String, String> messageChannelCache = new HashMap<>();

    private final JDA jda;


    @Override
    public Emote findEmoteById(String id) {
        log.debug("Looking for emote {}", id);
        for (Guild guild : jda.getGuilds()) {
            Emote emote = guild.getEmoteById(id);
            if (emote != null) {
                return emote;
            }
        }
        throw new NoSuchElementException(
            String.format("The emote with the id %s was not found", id));
    }

    @Override
    public Message findMessage(String jumpLink) {
        log.debug("Looking for message with jump link {}", jumpLink);
        Matcher matcher = jumpLinkMatcher.matcher(jumpLink);
        if (!matcher.find()) {
            throw new IllegalArgumentException("The provided jump link was not a jump link");
        }
        String guildId = matcher.group(2);
        String channelId = matcher.group(3);
        String messageId = matcher.group(4);

        Guild guild = jda.getGuildById(guildId);

        if (guild == null) {
            throw new NoSuchElementException("Guild not found");
        }

        TextChannel textChannel = guild.getTextChannelById(channelId);
        if (textChannel == null) {
            throw new NoSuchElementException("Channel not found");
        }

        Message message = textChannel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new NoSuchElementException("Message not found");
        }
        return message;
    }

    @Override
    public Message findMessageById(Guild guild, String message) {
        log.debug("Looking for message with id {}", message);
        if (jumpLinkMatcher.matcher(message).find()) {
            return findMessage(message);
        }

        if (messageChannelCache.containsKey(message)) {
            log.debug("Cache HIT for {}", message);
            TextChannel channel = guild.getTextChannelById(messageChannelCache.get(message));
            if (channel == null) {
                throw new NoSuchElementException("Channel not found");
            }
            Message m = channel.retrieveMessageById(message).complete();
            if (m != null) {
                return m;
            }
        }
        log.debug("Cache MISS for {}", message);

        for (TextChannel channel : guild.getTextChannels()) {
            Message m = channel.retrieveMessageById(message).complete();
            if (m != null) {
                log.debug("Caching channel for {} as {}", message, channel.getId());
                messageChannelCache.put(message, channel.getId());
                return m;
            }
        }
        log.debug("Message was not found");
        throw new NoSuchElementException("Message not found");
    }

    @Override
    public CompletableFuture<List<Message>> sendLongMessage(MessageChannel channel,
        String message) {
        StringBuilder buffer = new StringBuilder(2000);
        List<CompletableFuture<Message>> futures = new ArrayList<>();
        for (String line : message.split("\n")) {
            String toAppend = String.format("%s\n", line);
            if (toAppend.length() > 1990) {
                throw new IllegalArgumentException(
                    "Attempting to send a message that has a single line more than 1990 characters in length");
            }
            if (toAppend.length() + buffer.length() >= 1990) {
                futures.add(channel.sendMessage(buffer.toString()).submit());
                buffer = new StringBuilder(2000);
            }
            buffer.append(toAppend);
        }
        if (buffer.length() > 0) {
            futures.add(channel.sendMessage(buffer.toString()).submit());
        }
        CompletableFuture<List<Message>> future = new CompletableFuture<>();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((resp, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                } else {
                    future.complete(
                        futures.stream().map(f -> f.getNow(null)).collect(Collectors.toList()));
                }
            });
        return future;
    }
}
