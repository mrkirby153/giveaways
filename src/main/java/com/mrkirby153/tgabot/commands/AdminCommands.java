package com.mrkirby153.tgabot.commands;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.tgabot.entity.repo.ActionRepository;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.services.PollMessageService;
import com.mrkirby153.tgabot.services.PollReactionService;
import com.mrkirby153.tgabot.services.ReactionService;
import com.mrkirby153.tgabot.services.VoteConfirmationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import me.mrkirby153.kcutils.utils.IdGenerator;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@AllArgsConstructor
public class AdminCommands {

    private final VoteConfirmationService vs;
    private final PollReactionService prs;
    private final ReactionService rs;
    private final EventWaiter eventWaiter;
    private final CategoryRepository categoryRepo;
    private final PollMessageService pms;
    private final ActionRepository ar;

    private final Map<String, String> resetCode = new HashMap<>();
    private final IdGenerator generator = new IdGenerator(IdGenerator.Companion.getNUMBERS());

    @Command(name = "ping", clearance = 100)
    public void ping(Context context, CommandContext cmdContext) {
        long start = System.currentTimeMillis();
        log.debug("{} has used the ping command", context.getAuthor());
        context.getChannel().sendTyping().queue(v -> context.getChannel()
            .sendMessage("Pong! " + Time.INSTANCE.format(1, System.currentTimeMillis() - start))
            .queue());
    }

    @Command(name = "update-message", clearance = 100, parent = "voted")
    public void updateMessage(Context context, CommandContext cmdContext) {
        vs.refreshVotedMessage();
        context.getChannel().sendMessage(":ok_hand: Updated voted message!").queue();
    }

    @Command(name = "update-role", clearance = 100, parent = "voted")
    public void updateRole(Context context, CommandContext cmdContext) {
        vs.syncVotedUsers();
        context.getChannel().sendMessage(":ok_hand: Syncing voted users. This can take a bit")
            .queue();
    }

    @Command(name = "log-level", arguments = {"<logger:string>", "[level:string]"}, clearance = 100)
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

    @Command(name = "reaction-threshold", arguments = {"[new:int]"}, clearance = 100)
    public void reactionThreshold(Context context, CommandContext cmdContext) {
        if (cmdContext.has("new")) {
            prs.setThreshold(cmdContext.getNotNull("new"));
            context.getChannel().sendMessage(
                ":ok_hand: Reaction threshold has been set to `" + cmdContext.getNotNull("new")
                    + "`").queue();
        } else {
            context.getChannel().sendMessage("Reaction threshold is `" + prs.getThreshold() + "`")
                .queue();
        }
    }

    @Command(name = "reaction-executors", arguments = {"<new:int>"}, clearance = 100)
    public void reactionExecutors(Context context, CommandContext commandContext) {
        rs.resizeThreadPool(commandContext.getNotNull("new"));
        context.getChannel().sendMessage(
            "Reaction threadpool has been resized to " + commandContext.getNotNull("new")).queue();
    }

    @Command(name = "reset-all", arguments = {"[code:string...]"}, clearance = 100)
    public void resetEverything(Context context, CommandContext commandContext) {
        if (!commandContext.has("code")) {
            String code = generator.generate(10);
            this.resetCode.put(context.getAuthor().getId(), code);
            context.getChannel().sendMessage(
                ":warning: DANGER DANGER DANGER! :warning:"
                    + "\n\nThis will delete **ALL** categories and data and cannot be undone."
                    + " If you're _sure_ you want to do this run this command again with the code `"
                    + code + "`").queue();
            return;
        }
        String storedCode = resetCode.get(context.getAuthor().getId());
        if (commandContext.getNotNull("code").equals(storedCode)) {
            // Do the delete after confirming that they type "Delete all results"
            resetCode.remove(context.getAuthor().getId());
            context.getChannel().sendMessage(
                "Are you _sure_ you want to do this? If you are, type `Delete all results`")
                .queue(m -> {
                    eventWaiter.waitFor(GuildMessageReceivedEvent.class,
                        event -> event.getAuthor() == context.getAuthor(), event -> {
                            if (event.getMessage().getContentRaw()
                                .equalsIgnoreCase("Delete all results")) {
                                deleteEverything(context.getAuthor());
                                context.getChannel().sendMessage("Deleting all categories").queue();
                            } else {
                                context.getChannel().sendMessage("Aborted deletion").queue();
                            }
                        }, 10, TimeUnit.SECONDS, () -> {
                            m.editMessage("Did not receive a response in time. Aborting!").queue();
                        });
                });
        } else {
            throw new CommandException("Invalid code provided");
        }
    }

    private void deleteEverything(User requester) {
        log.warn("Deleting all categories. Requested by {}", requester);
        categoryRepo.findAll().forEach(category -> {
            pms.removeCategory(category);
            categoryRepo.delete(category);
        });
        ar.deleteAll();
    }
}
