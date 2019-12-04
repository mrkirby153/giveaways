package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
@Slf4j
public class GiveawayCommands {

    private final Pattern winnerPattern = Pattern.compile("^(\\d)+w$");

    private GiveawayService giveawayService;

    @Command(name = "start", arguments = {"<time:string>", "<prize:string...>"}, clearance = 100)
    public void createGiveaway(Context context, CommandContext cmdContext) {
        String time = cmdContext.getNotNull("time");
        String prizeStr = cmdContext.getNotNull("prize");
        int winners = 1;
        String[] parts = prizeStr.split(" ");
        if (parts.length >= 2) {
            String winnersQuestion = parts[0];
            Matcher matcher = winnerPattern.matcher(winnersQuestion);
            if (matcher.find()) {
                winners = Integer.parseInt(matcher.group(1));
            }
            log.debug("Winners are now {}", winners);
            prizeStr = prizeStr.replace(winnersQuestion, "").trim();
        }
        try {
            giveawayService.createGiveaway(context.getTextChannel(), prizeStr, winners, time);
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }


    @Command(name = "end", arguments = {"<mid:snowflake>"}, clearance = 100)
    public void endGiveaway(Context context, CommandContext cmdContext) {
        try {
            giveawayService.endGiveaway(cmdContext.getNotNull("mid"));
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }

    @Command(name = "reroll", arguments = {"<mid:snowflake>"}, clearance = 100)
    public void reroll(Context context, CommandContext cmdContext) {
        context.getChannel().sendMessage("Rerolling giveaway, hold tight!").queue();
        try {
            giveawayService.reroll(cmdContext.getNotNull("mid"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandException(e.getMessage());
        }
    }

}
