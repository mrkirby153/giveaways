package com.mrkirby153.giveaways

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.mrkirby153.giveaways.k8s.LeaderElection
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.util.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.mrkirby153.kcutils.ulid.generateUlid
import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

val log = LogManager.getLogger("LeaseTest")

fun main() {
    val client = Config.defaultClient()

    val httpClient =
        client.httpClient.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
    client.setHttpClient(httpClient)
    Configuration.setDefaultApiClient(client)

    Configuration.setDefaultApiClient(client)
    (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.TRACE

    val node = generateUlid()
    log.info("Starting as $node")
    val election = LeaderElection("giveaways-coordinator", node)
    election.onNewLeader {
        log.info("New leader: $it")
    }
    election.onStartLeading {
        log.info("Lock acquired")
    }
    election.onStoppedLeading {
        log.info("Stopped leading")
    }
    runBlocking {
        launch {
            election.run()
            println("Election ended")
        }
    }
}
