package com.mrkirby153.tgabot.commands;

import com.mrkirby153.botcore.command.CommandExecutor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class CommandManager {

    private static List<Class<?>> commands = new ArrayList<>();

    static {
        commands.add(AdminCommands.class);
        commands.add(CategoryCommands.class);
        commands.add(OptionCommands.class);
        commands.add(ActionCommands.class);
        commands.add(RoleCommands.class);
    }

    private final CommandExecutor executor;
    private final ApplicationContext context;

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        log.info("Registering {} command classes", commands.size());
        commands.forEach(clazz -> {
            log.debug("Registering {}", clazz);
            executor.register(context.getBean(clazz), clazz);
        });
    }

    @EventListener
    public void onMessage(GuildMessageReceivedEvent event) {
        if (event.getAuthor() == event.getJDA().getSelfUser() || event.getAuthor().isBot() || event
            .isWebhookMessage()) {
            // Ignore messages from ourselves, other bots, and webhooks
            return;
        }
        executor.execute(event.getMessage());
    }
}
