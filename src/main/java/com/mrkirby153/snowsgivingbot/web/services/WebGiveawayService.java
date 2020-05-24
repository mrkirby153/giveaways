package com.mrkirby153.snowsgivingbot.web.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.DiscordService;
import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import com.mrkirby153.snowsgivingbot.web.dto.AllGiveawaysDto;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class WebGiveawayService {

    private final GiveawayRepository giveawayRepository;
    private final EntrantRepository entrantRepository;
    private final ShardManager shardManager;
    private final DiscordService discordService;

    private final LoadingCache<String, String> channelNameCache = CacheBuilder.newBuilder()
        .maximumSize(1000).build(
            new CacheLoader<String, String>() {
                @Override
                public String load(String key) throws Exception {
                    TextChannel chan = shardManager.getTextChannelById(key);
                    if (chan == null) {
                        throw new IllegalStateException("Channel " + key + " was not found!");
                    }
                    return chan.getName();
                }
            });

    public AllGiveawaysDto getGiveaways(String guild, DiscordUser user) {
        List<String> visibleChannels = new ArrayList<>();
        Guild g = shardManager.getGuildById(guild);
        User u = shardManager.getUserById(user.getId());
        if (g != null && u != null) {
            Member member = g.getMember(u);
            if (member != null) {
                g.getTextChannels().forEach(channel -> {
                    if (discordService.canSeeChannel(member, channel)) {
                        visibleChannels.add(channel.getId());
                    }
                });
            }
        }
        if (visibleChannels.size() == 0) {
            return new AllGiveawaysDto(new ArrayList<>(), new ArrayList<>());
        }
        List<GiveawayEntity> activeGiveaways = giveawayRepository
            .getAllActiveGiveawaysInChannel(guild, visibleChannels);
        List<GiveawayEntity> inactiveGiveaways = giveawayRepository
            .getExpiredGiveaways(guild, visibleChannels,
                Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)));
        List<GiveawayEntrantEntity> entrants = entrantRepository
            .findAllByUserInGuild(user.getId(), guild);
        List<GiveawayDto> activeDtos = activeGiveaways.stream()
            .map(entity -> buildDto(entity, entrants)).collect(
                Collectors.toList());
        List<GiveawayDto> inactiveDtos = inactiveGiveaways.stream()
            .map(entity -> buildDto(entity, entrants)).collect(
                Collectors.toList());
        return new AllGiveawaysDto(activeDtos, inactiveDtos);
    }

    private GiveawayDto buildDto(GiveawayEntity giveaway, List<GiveawayEntrantEntity> entrants) {
        Optional<GiveawayEntrantEntity> entrant = entrants.stream()
            .filter(e -> e.getGiveaway().getId() == giveaway.getId()).findFirst();
        return new GiveawayDto(giveaway, getChannelName(giveaway.getChannelId()),
            entrant.isPresent());
    }

    private String getChannelName(String id) {
        try {
            return channelNameCache.get(id);
        } catch (ExecutionException e) {
            return "invalid-channel";
        }
    }
}
