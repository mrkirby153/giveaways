package com.mrkirby153.giveaways.config

import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.botcore.utils.SLF4J
import com.mrkirby153.giveaways.k8s.LeaderElection
import io.kubernetes.client.openapi.ApiClient
import me.mrkirby153.kcutils.coroutines.runAsync
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import java.util.concurrent.CompletableFuture

private const val LEADER_ELECTION_RESOURCE = "giveaways-leader"

@Configuration
class LeaderElectionConfiguration(
    @Value("\${giveaways.leaderelection.resource:$LEADER_ELECTION_RESOURCE}")
    private val resource: String,
    @Value("\${giveaways.leaderelection.namespace:default}")
    private val namespace: String,
    @Value("\${giveaways.leaderelection.identifier:}")
    identifier: String,
    private val eventPublisher: ApplicationEventPublisher,
    private val apiClient: ApiClient
) : DisposableBean {

    private val log by SLF4J
    private var leaderElectionJob: CompletableFuture<Unit>? = null

    private val nodeIdentifier = identifier.ifEmpty { generateUlid() }

    @Bean
    fun leaderElection() = LeaderElection(
        resource,
        nodeIdentifier,
        namespace = namespace,
        apiClient = apiClient
    ).apply {
        onNewLeader {
            log.trace("New leader: $it")
            eventPublisher.publishEvent(LeaderChangeEvent(it))
        }
        onStartLeading {
            log.info("Became leader")
            eventPublisher.publishEvent(LeadershipAcquiredEvent)
        }
        onStoppedLeading {
            log.info("Lost leadership")
            eventPublisher.publishEvent(LeadershipLostEvent)
        }
    }

    @EventListener
    fun onReady(event: BotReadyEvent) {
        this.leaderElectionJob = runAsync {
            log.info("Starting leader election")
            leaderElection().run()
            log.warn("Leader election terminated")
        }
    }

    override fun destroy() {
        log.info("Stopping leader election job")
        leaderElectionJob?.cancel(true)
    }
}

sealed interface LeadershipChangeEvent

object LeadershipAcquiredEvent : LeadershipChangeEvent

object LeadershipLostEvent : LeadershipChangeEvent

class LeaderChangeEvent(val newLeader: String) : LeadershipChangeEvent