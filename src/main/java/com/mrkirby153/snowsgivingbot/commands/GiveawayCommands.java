package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
@Slf4j
public class GiveawayCommands {

    private final Pattern winnerPattern = Pattern.compile("^(\\d+)w$");

    private final GiveawayService giveawayService;
    private final EntrantRepository er;
    private final GiveawayRepository gr;

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
                prizeStr = prizeStr.replace(winnersQuestion, "").trim();
            }
            log.debug("Winners are now {}", winners);
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

    @Command(name = "fakeusers", arguments = {"<id:int>", "<users:int>"}, clearance = 101)
    public void addFakeUsers(Context context, CommandContext commandContext) {
        int id = commandContext.getNotNull("id");
        GiveawayEntity ge = gr.findById(Integer.valueOf(id).longValue())
            .orElseThrow(() -> new CommandException("KABOOM. giveaway not found"));
        int users = commandContext.getNotNull("users");
        context.getChannel().sendMessage("Adding " + users + " fake users to giveaway " + id)
            .queue();
        List<GiveawayEntrantEntity> entries = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            GiveawayEntrantEntity gee = new GiveawayEntrantEntity(ge, "" + i);
            entries.add(gee);
        }
        er.saveAll(entries);
        context.getChannel().sendMessage("Done").queue();
    }

}
