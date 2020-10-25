package com.mrkirby153.snowsgivingbot.config;

import com.mrkirby153.botcore.command.CommandExecutor;
import com.mrkirby153.botcore.command.CommandExecutor.MentionMode;
import com.mrkirby153.botcore.command.args.ArgumentParseException;
import com.mrkirby153.snowsgivingbot.services.PermissionService;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import com.mrkirby153.snowsgivingbot.services.setting.settings.StringSetting;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@Slf4j
public class CommandConfig {

    private static final Pattern ID_PATTERN = Pattern.compile("\\d{17,18}");
    private final PermissionService permissionManager;
    private final SettingService settingService;
    private String prefix;
    private String owner;


    public CommandConfig(@Value("${bot.prefix:!}") String prefix, @Value("${bot.owner:}") String owner,
        PermissionService permissionManager, SettingService settingService) {
        this.prefix = prefix;
        this.owner = owner;
        this.permissionManager = permissionManager;
        this.settingService = settingService;
        // Override the command prefix setting
        Settings.COMMAND_PREFIX = new StringSetting(Settings.COMMAND_PREFIX.getKey(), prefix);
    }

    @Bean
    public CommandExecutor commandExecutor(ShardManager shardManager) {
        CommandExecutor ex = new CommandExecutor(prefix, MentionMode.OPTIONAL, null, shardManager);
        ex.setAlertNoClearance(false);
        ex.setAlertUnknownCommand(false);
        ex.setClearanceResolver(member -> {
            if (member.getUser().getId().equalsIgnoreCase(owner)) {
                return 101;
            }
            if (permissionManager.hasPermission(member)) {
                return 100;
            } else {
                return 0;
            }
        });
        ex.addContextResolver("idormention", options -> {
            String next = options.pop();
            Matcher m = ID_PATTERN.matcher(next);
            if (!m.find()) {
                throw new ArgumentParseException("Could not extract an id from `" + next + "`");
            }
            return m.group(0);
        });
        ex.addContextResolver("boolean", options -> {
            String next = options.pop();
            if (next.equalsIgnoreCase("true")) {
                return true;
            } else if (next.equalsIgnoreCase("false")) {
                return false;
            } else {
                throw new ArgumentParseException(
                    "`" + next + "` is not a boolean. Must be true or false");
            }
        });
        ex.overridePrefixResolver((Function<Message, String>) message -> {
            if (!message.isFromGuild()) {
                return prefix; // If we're in DMs, return the default prefix
            }
            return settingService.get(Settings.COMMAND_PREFIX, message.getGuild());
        });
        return ex;
    }
}
