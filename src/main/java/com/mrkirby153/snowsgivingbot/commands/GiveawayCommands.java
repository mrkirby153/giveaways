package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GiveawayCommands {

    private GiveawayService giveawayService;

    @Command(name = "start", arguments = {"<time:string>", "<winners:int>", "<name:string...>"})
    public void createGiveaway(Context context, CommandContext cmdContext) {
        String time = cmdContext.getNotNull("time");
        int winners = cmdContext.getNotNull("winners");
        String name = cmdContext.getNotNull("name");
        giveawayService.createGiveaway(context.getTextChannel(), name, winners, time);
    }



}
