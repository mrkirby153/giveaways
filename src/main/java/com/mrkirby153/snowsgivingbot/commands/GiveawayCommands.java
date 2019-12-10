package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        startGiveaway(context, cmdContext, false);
    }


    @Command(name = "sstart", arguments = {"<time:string>", "<prize:string...>"}, clearance = 100)
    public void privateGiveaway(Context context, CommandContext cmdContext) {
        startGiveaway(context, cmdContext, true);
    }

    private void startGiveaway(Context context, CommandContext cmdContext, boolean secret) {
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
            giveawayService
                .createGiveaway(context.getTextChannel(), prizeStr, winners, time, secret);
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

    @Command(name = "reroll", arguments = {"<mid:snowflake>", "[users:string...]"}, clearance = 100)
    public void reroll(Context context, CommandContext cmdContext) {
        context.getChannel().sendMessage("Rerolling giveaway...").queue();
        try {
            String[] users = null;
            String toReroll = cmdContext.get("users");
            if (toReroll != null) {
                users = toReroll.split(",");
            }
            giveawayService.reroll(cmdContext.getNotNull("mid"), users);
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

    @Command(name = "secret", arguments = {"<mid:snowflake>", "<state:boolean>"}, clearance = 100)
    public void setPrivate(Context context, CommandContext commandContext) {
        GiveawayEntity entity = gr.findByMessageId(commandContext.getNotNull("mid"))
            .orElseThrow(() -> new CommandException("Giveaway was not found"));
        if (entity.getState() == GiveawayState.ENDED) {
            throw new CommandException("Giveaway has already ended");
        }
        entity.setSecret(commandContext.getNotNull("state"));
        gr.save(entity);
        String s = commandContext.getNotNull("state") ? "is now" : "no longer is";
        context.getChannel()
            .sendMessage(":ok_hand: **" + entity.getName() + "** " + s + " a secret giveaway")
            .queue();
    }

    @Command(name = "winners", arguments = {"<mid:snowflake>"}, clearance = 100)
    public void getWinners(Context context, CommandContext commandContext) {
        GiveawayEntity entity = gr.findByMessageId(commandContext.getNotNull("mid"))
            .orElseThrow(() -> new CommandException("Giveaway was not found"));
        if (entity.getState() != GiveawayState.ENDED) {
            throw new CommandException("Giveaway has not ended yet!");
        }
        String[] winners = entity.getFinalWinners().split(",");

        String winnerString = Arrays.stream(winners).map(String::trim).map(s -> "<@!" + s + ">")
            .collect(Collectors.joining(", "));
        context.getChannel()
            .sendMessage("The winners for **" + entity.getName() + "** are\n" + winnerString)
            .queue();
    }
}
