package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.config.RabbitMQConfiguration;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayEnterEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import com.rabbitmq.client.Channel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
// TODO: 4/9/21 Make this an interface
public class RabbitMQService {

    private final RabbitTemplate rabbitTemplate;
    private final SettingService settingService;
    private final StandaloneWorkerService standaloneWorkerService;
    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    private final GiveawayRepository giveawayRepository;
    private final EntrantRepository entrantRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ShardManager shardManager;
    private final Map<Long, RunningQueueWorker> runningQueues = new ConcurrentHashMap<>();

    private int prefetchCount = 10;

    @EventListener
    public void onGiveawayStart(GiveawayStartedEvent event) {
        if (!standaloneWorkerService.isStandalone(event.getGiveaway().getGuildId())) {
            return;
        }
        log.debug("Publishing start to giveaway work queue");
        sendToWorker(event.getGiveaway());
    }

    public void sendToWorker(GiveawayEntity giveaway) {
        ConfiguredGiveawayEmote cge = settingService
            .get(Settings.GIVEAWAY_EMOTE, giveaway.getGuildId());
        rabbitTemplate.convertAndSend(RabbitMQConfiguration.GIVEAWAY_WORK_QUEUE,
            new GiveawayStartMsg(giveaway.getId(), giveaway.getMessageId(),
                cge != null ? cge.getEmote() : null));
        startQueueHandler(giveaway);
    }

    public void removeFromWorker(GiveawayEntity giveaway) {
        rabbitTemplate.convertAndSend(RabbitMQConfiguration.GIVEAWAY_STATE_EXCHANGE, "",
            Long.toString(giveaway.getId()));
        stopQueueHandler(giveaway);
    }

    @EventListener
    public void onGiveawayEnd(GiveawayEndedEvent event) {
        if (!standaloneWorkerService.isStandalone(event.getGiveaway().getGuildId())) {
            return;
        }
        log.debug("Publishing end event");
        removeFromWorker(event.getGiveaway());
    }

    /**
     * Updates the prefetch count for running giveaways
     *
     * @param newCount The new count to update to
     */
    public void updatePrefetchCount(int newCount) {
        this.prefetchCount = newCount;
        runningQueues.forEach((id, worker) -> {
            worker.container.setPrefetchCount(newCount);
            // Do we need to restart the container? probably
            worker.container.stop();
            worker.container.start();
        });
    }

    /**
     * Starts all queue handlers for a guild
     *
     * @param guild The guild
     */
    public void startAll(Guild guild) {
        giveawayRepository.findAllByGuildIdAndState(guild.getId(), GiveawayState.RUNNING)
            .forEach(this::startQueueHandler);
    }

    public long queueSize(GiveawayEntity entity) {
        return queueSize(entity.getId());
    }

    public long queueSize(long giveawayId) {
        String key = String.format("giveaway_entrants.%d", giveawayId);
        Properties properties = amqpAdmin.getQueueProperties(key);
        if (properties == null) {
            return 0;
        }
        return Long.parseLong(properties.getProperty(RabbitAdmin.QUEUE_MESSAGE_COUNT.toString()));
    }

    public Map<Long, Long> runningQueueSizes() {
        Map<Long, Long> l = new HashMap<>();
        runningQueues.keySet().forEach(id -> l.put(id, queueSize(id)));
        return l;
    }

    private void startQueueHandler(GiveawayEntity entity) {
        if (runningQueues.containsKey(entity.getId())) {
            log.debug("Not starting {} as it's already been started", entity);
            return;
        }
        log.debug("Starting queue handler for {}", entity);
        String queueName = String.format("giveaway_entrants.%d", entity.getId());
        Queue queue = new Queue(queueName);
        amqpAdmin.declareQueue(queue);
        SimpleMessageListenerContainer smlc = new SimpleMessageListenerContainer();
        smlc.setMessageListener(new EntryHandler(entity, this));
        smlc.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        smlc.setPrefetchCount(prefetchCount);
        smlc.setConnectionFactory(connectionFactory);
        smlc.setQueueNames(queueName);
        smlc.setAmqpAdmin(amqpAdmin);
        smlc.start();
        this.runningQueues.put(entity.getId(), new RunningQueueWorker(queue, smlc));
    }

    private void stopQueueHandler(GiveawayEntity entity) {
        log.debug("Stopping queue handler for {}", entity);
        RunningQueueWorker worker = runningQueues.remove(entity.getId());
        if (worker != null) {
            worker.container.stop();
            amqpAdmin.deleteQueue(worker.queue.getName());
        }
    }

    @Data
    private static class GiveawayStartMsg {

        private final Long giveawayId;
        private final String messageId;
        private final String emote;
    }

    @Data
    private static class RunningQueueWorker {

        private final Queue queue;
        private final SimpleMessageListenerContainer container;
    }

    @RequiredArgsConstructor
    @Slf4j
    private static class EntryHandler implements ChannelAwareMessageListener {

        private final GiveawayEntity giveaway;
        private final RabbitMQService service;

        @Override
        public void onMessage(Message message, Channel channel) throws Exception {
            log.trace("Received message {}", message);
            try {
                String userId = new String(message.getBody());
                userId = userId.replaceAll("\"(.*)\"", "$1");
                log.debug("Entering {} in giveaway {}", userId, giveaway);

                if (!service.entrantRepository.existsByGiveawayAndUserId(giveaway, userId)) {
                    service.entrantRepository.save(new GiveawayEntrantEntity(giveaway, userId));
                    service.shardManager.retrieveUserById(userId).queue(user -> {
                        log.debug("Dispatching GiveawayEnterEvent for {} and {}", user, giveaway);
                        service.applicationEventPublisher
                            .publishEvent(new GiveawayEnterEvent(user, giveaway));
                    });
                }

            } catch (Exception e) {
                log.error("Error processing entry", e);
            } finally {
                log.trace("Acking entry {}", message.getMessageProperties().getDeliveryTag());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
        }
    }
}
