package com.mrkirby153.snowsgivingbot.config;

import com.mrkirby153.botcore.command.slashcommand.SlashCommandAvailability;
import com.mrkirby153.botcore.command.slashcommand.SlashCommandExecutor;
import com.mrkirby153.snowsgivingbot.services.impl.PermissionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlashCommandConfiguration {

    @Bean
    public SlashCommandExecutor slashCommandExecutor(@Value("${bot.owner:}") String owner,
        PermissionManager permissionManager) {
        SlashCommandExecutor slashCommandExecutor = new SlashCommandExecutor(member -> {
            if (member.getUser().getId().equalsIgnoreCase(owner)) {
                return 101;
            }
            if (permissionManager.hasPermission(member)) {
                return 100;
            } else {
                return 0;
            }
        });
        // Slash commands are only available in a guild
        slashCommandExecutor.setDefaultCommandAvailability(SlashCommandAvailability.GUILD);
        return slashCommandExecutor;
    }
}
