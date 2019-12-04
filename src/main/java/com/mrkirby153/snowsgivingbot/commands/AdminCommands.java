package com.mrkirby153.snowsgivingbot.commands;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class AdminCommands {

    @Command(name = "ping", clearance = 100)
    public void ping(Context context, CommandContext cmdContext) {
        long start = System.currentTimeMillis();
        context.getChannel().sendTyping().queue(v -> {
            context.getChannel()
                .sendMessage("Pong! " + Time.INSTANCE.format(1, System.currentTimeMillis() - start))
                .queue();
        });
    }

    @Command(name = "log-level", arguments = {"<logger:string>", "[level:string]"}, clearance = 101)
    public void logLevel(Context context, CommandContext cmdContext) {
        String loggerName = cmdContext.get("logger");
        String level = cmdContext.get("level");

        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        if (level != null) {
            try {
                Level prev = logger.getEffectiveLevel();
                Level lvl = Level.valueOf(level.toUpperCase());
                logger.setLevel(lvl);
                log.info("Log level changed from {} -> {} for [{}]", prev, lvl, loggerName);
            } catch (IllegalArgumentException e) {
                throw new CommandException("Log level not recognized");
            }

            context.getChannel().sendMessage(
                "Logger `" + loggerName + "` has been set to level `" + level + "`").queue();
        } else {
            context.getChannel().sendMessage(
                "Logger `" + loggerName + "` is at level `" + logger.getEffectiveLevel().toString()
                    + "`").queue();
        }
    }
}
