package com.mrkirby153.tgabot.config;

import com.mrkirby153.botcore.command.CommandExecutor;
import com.mrkirby153.botcore.command.CommandExecutor.MentionMode;
import com.mrkirby153.botcore.command.args.ArgumentParseException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class CommandConfig {

    private static final Pattern ID_PATTERN = Pattern.compile("\\d{17,18}");
    private String prefix;
    private String admins;


    public CommandConfig(@Value("${bot.prefix:!}") String prefix,
        @Value("${bot.admins:}") String admins) {
        this.prefix = prefix;
        this.admins = admins;
    }

    @Bean
    public CommandExecutor commandExecutor(JDA jda) {
        CommandExecutor ex = new CommandExecutor(prefix, MentionMode.OPTIONAL, jda, null);
        ex.setAlertNoClearance(false);
        ex.setAlertUnknownCommand(false);
        ex.setClearanceResolver(member -> {
            String[] parts = this.admins.split(",");
            List<String> roles = Arrays.stream(parts).filter(s -> s.startsWith("r:"))
                .map(s -> s.substring(2)).collect(Collectors.toList());
            List<String> users = Arrays.stream(parts).filter(s -> s.startsWith("u:"))
                .map(s -> s.substring(2)).collect(
                    Collectors.toList());

            if (users.contains(member.getId())) {
                return 100;
            }
            if (member.getRoles().stream().anyMatch(role -> roles.contains(role.getId()))) {
                return 100;
            }
            return 0;
        });
        ex.addContextResolver("idormention", options -> {
            String next = options.pop();
            Matcher m = ID_PATTERN.matcher(next);
            if (!m.find()) {
                throw new ArgumentParseException("Could not extract an id from `" + next + "`");
            }
            return m.group(0);
        });
        return ex;
    }
}
