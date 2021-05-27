package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.slashcommand.SlashCommandExecutor;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlashCommandManager {

    private static List<Class<?>> slashCommands = new ArrayList<>();

    static {
        slashCommands.add(AdminSlashCommands.class);
        slashCommands.add(GiveawaySlashCommands.class);
        slashCommands.add(InfoSlashCommands.class);
        slashCommands.add(ManagementSlashCommands.class);
    }

    private final SlashCommandExecutor slashCommandExecutor;
    private final ShardManager shardManager;
    private final ApplicationContext applicationContext;

    @Value("${bot.slash-command.guilds:}")
    private String slashCommandGuilds;


    @EventListener
    public void onSlashCommand(SlashCommandEvent event) {
        log.debug("Executing slash command {}", event.getName());
        slashCommandExecutor.executeSlashCommand(event);
    }

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        log.info("Discovering slash commands");
        slashCommands.forEach(clazz -> {
            try {
                slashCommandExecutor.discoverAndRegisterSlashCommands(applicationContext.getBean(clazz), clazz);
            } catch (Exception e) {
                log.error("Could not register {}", clazz, e);
            }
        });

        List<CommandData> slashCommands = slashCommandExecutor.flattenSlashCommands();
        if (slashCommandGuilds.isBlank()) {
            log.info("Updating slash commands globally: {}", slashCommands.size());
            shardManager.getShards().get(0).updateCommands().addCommands(slashCommands).queue(v -> {
                log.info("Updated slash commands globally: {}", slashCommands.size());
            });
        } else {
            log.info("Updating slash commands in guilds {}", slashCommandGuilds);
            for (String guild : slashCommandGuilds.split(",")) {
                Guild g = shardManager.getGuildById(guild);
                if (g == null) {
                    log.warn("Could not update slash commands in {}. Guild not found", guild);
                    continue;
                }
                g.updateCommands().addCommands(slashCommands).queue(v -> {
                    log.info("Updated slash commands in {}: {}", g, slashCommands.size());
                });
            }
        }
    }
}
