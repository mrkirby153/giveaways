package com.mrkirby153.tgabot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.tgabot.entity.ActionLog;
import com.mrkirby153.tgabot.services.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class ActionCommands {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
    private static final int PER_PAGE = 20;

    private final LogService als;

    @Command(name = "logs", clearance = 100, arguments = {"<id:idormention>", "[page:int]"})
    public void sendActionLog(Context context, CommandContext cmdContext) {
        context.getChannel().sendTyping().queue();
        int page = !cmdContext.has("page") ? 1 : cmdContext.getNotNull("page");
        String id = cmdContext.getNotNull("id");

        Page<ActionLog> actions = als.getActions(id, PageRequest.of(page - 1, PER_PAGE,
            Sort.by("timestamp").descending()));

        StringBuilder msg = new StringBuilder();
        msg.append("Action log for **").append(id).append("**\n");
        msg.append("Page ").append(page).append(" of ").append(actions.getTotalPages())
            .append("\n");
        actions.forEach(action -> {
            msg.append(" - `").append(
                DATE_FORMAT.format(new Date(action.getTimestamp().toInstant().toEpochMilli())))
                .append("` **").append(action.getType().getFriendlyName()).append("**");
            if (!action.getData().isEmpty()) {
                msg.append(": ").append(action.getData());
            }
            msg.append("\n");
        });
        context.getChannel().sendMessage(msg.toString()).queue();
    }

    @Command(name = "all", clearance = 100, parent = "logs", arguments = {"<id:idormention>"})
    public void sendAllLogs(Context context, CommandContext cmdContext) {
        context.getChannel().sendTyping().queue();

        Page<ActionLog> actions = als.getActions(cmdContext.getNotNull("id"), Pageable.unpaged());

        StringBuilder sb = new StringBuilder();
        sb.append("===============\n");
        sb.append("Actions for ").append(cmdContext.<String>getNotNull("id")).append("\n");
        sb.append("===============\n");

        actions.forEach(action -> {
            sb.append(
                DATE_FORMAT.format(new Date(action.getTimestamp().toInstant().toEpochMilli())));
            sb.append(" - ").append(action.getType().getFriendlyName()).append(": ")
                .append(action.getData()).append("\n");
        });
        context.getChannel()
            .sendFile(sb.toString().getBytes(), "actions_" + cmdContext.getNotNull("id") + ".txt")
            .queue();
    }
}
