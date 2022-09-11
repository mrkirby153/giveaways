package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.spring.JDAAutoConfiguration
import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.giveaways.utils.log
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import kotlin.reflect.KClass


private val slashCommands: List<KClass<out DslSlashCommandProvider>> = listOf(
    GiveawayCommands::class,
    TestCommands::class
)

@Configuration
@Import(JDAAutoConfiguration::class)
class CommandConfiguration(
    @Value("\${bot.commands.guilds:}")
    private val slashCommandGuilds: String,
    private val shardManager: ShardManager,
    private val context: ApplicationContext,
    private val slashCommandExecutor: DslCommandExecutor
) {

    @Bean("slashCommandProviders")
    fun slashCommandProviders() = slashCommands.map { context.getBean(it.java) }


    @EventListener
    fun onReady(botReadyEvent: BotReadyEvent) {
        log.info("Registering Slash Commands")
        slashCommandProviders().forEach {
            log.debug("Registering slash commands in ${it.javaClass}")
            it.register(slashCommandExecutor)
        }
        val guilds = slashCommandGuilds.split(",")
        if (guilds.isNotEmpty()) {
            slashCommandExecutor.commit(shardManager.shards.first(), *guilds.toTypedArray())
                .thenRun {
                    log.info("Registered slash commands in $guilds")
                }
        } else {
            slashCommandExecutor.commit(shardManager.shards.first()).thenRun {
                log.info("Registered slash commands globally")
            }
        }
    }
}