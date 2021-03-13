package com.mrkirby153.snowsgivingbot.config;

import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.services.EventService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

@Slf4j
@Configuration
public class BotConfig {

    public static final String GREEN_CHECK = "✅";
    public static final String RED_CROSS = "❌";

    private final String token;
    private final EventService springJdaShim;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;


    public BotConfig(@Value("${bot.token}") String token, EventService service,
        ApplicationEventPublisher eventPublisher, TaskScheduler taskScheduler) {
        this.token = token;
        this.springJdaShim = service;
        this.eventPublisher = eventPublisher;
        this.taskScheduler = taskScheduler;
    }

    @Bean
    public ShardManager shardManager() throws Exception {
        log.info("Starting bot");
        DefaultShardManagerBuilder shardBuilder = DefaultShardManagerBuilder
            .create(this.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.GUILD_EMOJIS)
            .disableCache(
                CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.CLIENT_STATUS)
            .addEventListeners(springJdaShim)
            .setMemberCachePolicy(MemberCachePolicy.DEFAULT)
            .setChunkingFilter(ChunkingFilter.NONE)
            .setLargeThreshold(250)
            .setStatus(OnlineStatus.DO_NOT_DISTURB);
        ShardManager shardManager = shardBuilder.build();
        shardManager.addEventListener(new ReadyListener(shardManager));
        return shardManager;
    }

    @Bean
    public EventWaiter eventWaiter(ShardManager shardManager) {
        EventWaiter waiter = new EventWaiter();
        shardManager.addEventListener(waiter);
        return waiter;
    }

    @AllArgsConstructor
    private class ReadyListener extends ListenerAdapter {

        private final ShardManager shardManager;

        @Override
        public void onReady(@Nonnull ReadyEvent event) {
            log.info("Shard {} / {} is ready", event.getJDA().getShardInfo().getShardId(),
                event.getJDA().getShardInfo().getShardTotal());
            List<JDA> connecting = shardManager.getShards().stream()
                .filter(jda -> jda.getStatus() != Status.CONNECTED
                    && event.getJDA().getShardInfo().getShardId() != jda.getShardInfo()
                    .getShardId()).collect(
                    Collectors.toList());
            if (connecting.size() == 0) {
                log.info("All shards have connected");
                log.info("On {} guilds", shardManager.getGuilds().size());
                shardManager.removeEventListener(this);
                shardManager.setStatus(OnlineStatus.ONLINE);
                if (event.getJDA().getShardInfo().getShardTotal() == 1) {
                    // We need to delay ready for 250ms for some reason
                    // TODO: 5/20/20 Investigate this
                    taskScheduler
                        .schedule(() -> eventPublisher.publishEvent(new AllShardsReadyEvent()),
                            Instant.now().plusMillis(1500));
                } else {
                    eventPublisher.publishEvent(new AllShardsReadyEvent());
                }
            } else {
                log.debug("{} shards waiting", connecting.size());
            }
        }
    }
}
