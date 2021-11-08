package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.slashcommand.SlashCommandExecutor;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class SlashCommandManager {

    private static List<Class<?>> slashCommands = new ArrayList<>();
    private static List<Class<?>> adminGuildSlashCommands = new ArrayList<>();

    static {
        slashCommands.add(AdminSlashCommands.class);
        slashCommands.add(GiveawaySlashCommands.class);
        slashCommands.add(InfoSlashCommands.class);
        slashCommands.add(ManagementSlashCommands.class);

        adminGuildSlashCommands.add(AdminGuildSlashCommands.class);
    }

    private final SlashCommandExecutor slashCommandExecutor;
    private final ShardManager shardManager;
    private final ApplicationContext applicationContext;

    private final SlashCommandExecutor adminCommandExecutor;

    @Value("${bot.slash-command.guilds:}")
    private String slashCommandGuilds;
    @Value("${bot.slash-command.admin-guilds:}")
    private String adminSlashCommandGuilds;

    public SlashCommandManager(SlashCommandExecutor slashCommandExecutor, ShardManager shardManager,
        ApplicationContext applicationContext, @Value("${bot.owner:}") String owner) {
        this.slashCommandExecutor = slashCommandExecutor;
        this.shardManager = shardManager;
        this.applicationContext = applicationContext;
        adminCommandExecutor = new SlashCommandExecutor(
            member -> member.getId().equals(owner) ? 100 : 0);
    }

    @EventListener
    public void onSlashCommand(SlashCommandEvent event) {
        log.debug("Executing slash command {}", event.getName());
        if (event.isFromGuild() && Arrays.asList(adminSlashCommandGuilds.split(","))
            .contains(event.getGuild().getId()) || event.getName().equals("remove")) {
            if (adminCommandExecutor.executeSlashCommandIfAble(event)) {
                return;
            }
        }
        slashCommandExecutor.executeSlashCommand(event);
    }

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        log.info("Discovering slash commands");
        slashCommands.forEach(clazz -> {
            try {
                slashCommandExecutor.discoverAndRegisterSlashCommands(
                    applicationContext.getBean(clazz), clazz);
            } catch (Exception e) {
                log.error("Could not register {}", clazz, e);
            }
        });

        adminGuildSlashCommands.forEach(clazz -> {
            try {
                adminCommandExecutor.discoverAndRegisterSlashCommands(
                    applicationContext.getBean(clazz), clazz);
            } catch (Exception e) {
                log.error("Could not register admin command {}", clazz, e);
            }
        });

        List<CommandData> slashCommands = slashCommandExecutor.flattenSlashCommands();
        List<CommandData> adminSlashCommands = adminCommandExecutor.flattenSlashCommands();
        if (slashCommandGuilds.isBlank()) {
            log.info("Updating slash commands globally: {}", slashCommands.size());
            shardManager.getShards().get(0).updateCommands().addCommands(slashCommands)
                .queue(v -> log.info("Updated slash commands globally: {}", slashCommands.size()));
            Arrays.stream(adminSlashCommandGuilds.split(",")).map(shardManager::getGuildById)
                .filter(
                    Objects::nonNull)
                .forEach(g -> g.updateCommands().addCommands(adminSlashCommands)
                    .queue(v -> log.info("Updated admin slash commands in {}: {}", g,
                        adminSlashCommands.size())));
        } else {
            log.info("Updating slash commands in guilds {}", slashCommandGuilds);
            for (String guild : slashCommandGuilds.split(",")) {
                Guild g = shardManager.getGuildById(guild);
                if (g == null) {
                    log.warn("Could not update slash commands in {}. Guild not found", guild);
                    continue;
                }
                List<CommandData> combinedSlashCommands = new ArrayList<>(slashCommands);
                if (Arrays.asList(adminSlashCommandGuilds.split(",")).contains(guild)) {
                    log.info("Adding {} admin slash commands to {}", adminSlashCommands.size(), g);
                    combinedSlashCommands.addAll(adminSlashCommands);
                }
                g.updateCommands().addCommands(combinedSlashCommands).queue(
                    v -> log.info("Updated slash commands in {}: {}", g,
                        combinedSlashCommands.size()));
            }
        }
    }
}
