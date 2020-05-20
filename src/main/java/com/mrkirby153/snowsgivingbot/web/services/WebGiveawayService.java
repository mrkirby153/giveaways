package com.mrkirby153.snowsgivingbot.web.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.web.DiscordUser;
import com.mrkirby153.snowsgivingbot.web.dto.GiveawayDto;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@AllArgsConstructor
public class WebGiveawayService {

    private final GiveawayRepository giveawayRepository;
    private final EntrantRepository entrantRepository;
    private final ShardManager shardManager;

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

    public List<GiveawayDto> getGiveaways(String guild, DiscordUser user) {
        List<GiveawayEntity> giveaways = giveawayRepository.findAllByGuildId(guild);
        List<GiveawayEntrantEntity> entrants = entrantRepository
            .findAllByUserInGuild(user.getId(), guild);

        List<GiveawayDto> giveawayDtos = new ArrayList<>();
        giveaways.forEach(giveaway -> {
            Optional<GiveawayEntrantEntity> entrant = entrants.stream()
                .filter(entrantEntity -> entrantEntity.getGiveaway().getId() == giveaway.getId())
                .findFirst();
            giveawayDtos.add(
                new GiveawayDto(giveaway.getId(), giveaway.getName(), giveaway.getChannelId(),
                    getChannelName(giveaway.getChannelId()),
                    giveaway.getEndsAt(), entrant.isPresent(), giveaway.getState()));
        });
        return giveawayDtos;
    }

    private String getChannelName(String id) {
        try {
            return channelNameCache.get(id);
        } catch (ExecutionException e) {
            return null;
        }
    }
}
