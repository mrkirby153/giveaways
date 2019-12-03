package com.mrkirby153.tgabot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.tgabot.config.BotConfig;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.OptionRole;
import com.mrkirby153.tgabot.entity.repo.OptionRepository;
import com.mrkirby153.tgabot.entity.repo.OptionRoleRepository;
import com.mrkirby153.tgabot.services.OptionRoleService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleCommands {

    private final OptionRoleService ors;
    private final OptionRoleRepository orr;
    private final OptionRepository or;
    private final EventWaiter ew;

    @Command(name = "add", parent = "option-role", clearance = 100, arguments = {"<option:int>",
        "<role:idormention>"})
    public void addOptionRole(Context context, CommandContext commandContext) {
        Long option = commandContext.<Integer>getNotNull("option").longValue();
        String role = commandContext.getNotNull("role");

        Role r = context.getGuild().getRoleById(role);
        if (r == null) {
            throw new CommandException("That role does not exist!");
        }

        Option o = or.findById(option)
            .orElseThrow(() -> new CommandException("That option was not found"));

        OptionRole optionRole = new OptionRole(o, r.getId());
        optionRole = orr.save(optionRole);
        context.getChannel().sendMessage(
            "Added **" + r.getName() + "** for **" + o.getName() + "** (" + optionRole.getId()
                + ")\n\nTo retroactively apply, sync the option roles with option-role sync")
            .queue();
        ors.refreshOptionRoleCache();
    }

    @Transactional
    @Command(name = "list", parent = "option-role", clearance = 100)
    public void listOptionRoles(Context context, CommandContext commandContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("The following option roles are configured: ```");
        // TODO: 11/13/19 Maybe make this only get the ones in the current guild?
        orr.findAll().forEach(optionRole -> {
            Role r = context.getGuild().getRoleById(optionRole.getRoleId());
            String msg =
                optionRole.getId() + ". " + (r != null ? r.getName() : "") + " → " + optionRole
                    .getOption()
                    .getCategory().getName() + ": " + optionRole.getOption().getName() + "\n";
            if (sb.length() + msg.length() >= 1990) {
                sb.append("```");
                context.getChannel().sendMessage(sb.toString()).queue();
                sb.setLength(0);
                sb.append("```");
            } else {
                sb.append(msg);
            }
        });
        if (sb.length() > 0) {
            sb.append("```");
            context.getChannel().sendMessage(sb.toString()).queue();
        }
    }

    @Command(name = "delete", parent = "option-role", clearance = 100, arguments = {"<id:int>"})
    public void deleteOptionRole(Context context, CommandContext commandContext) {
        OptionRole option = orr.findById(commandContext.<Integer>getNotNull("id").longValue())
            .orElseThrow(() -> new CommandException("Option role not found"));
        Role r = context.getGuild().getRoleById(option.getRoleId());

        orr.delete(option);
        ors.refreshOptionRoleCache();
        context.getChannel().sendMessage("Deleted!").queue();
        if (r != null) {
            List<Member> members = r.getGuild().getMembers().stream()
                .filter(m -> m.getRoles().contains(r)).collect(
                    Collectors.toList());
            if (members.size() > 0) {
                context.getChannel().sendMessage(
                    "Do you wish to remove this role from **" + members.size() + "** users?")
                    .queue(message -> {
                        message.addReaction(BotConfig.GREEN_CHECK)
                            .queue(v -> message.addReaction(BotConfig.RED_CROSS).queue());

                        ew.waitFor(MessageReactionAddEvent.class, event ->
                                event.getMessageId().equals(message.getId()) && event.getUser().getId()
                                    .equals(context.getAuthor().getId()) && (
                                    event.getReactionEmote().getName().equals(BotConfig.GREEN_CHECK)
                                        || event.getReactionEmote().getName()
                                        .equals(BotConfig.RED_CROSS)),
                            event -> {
                                if (event.getReactionEmote().getName()
                                    .equals(BotConfig.RED_CROSS)) {
                                    message.editMessage("Canceled").queue();
                                    message.clearReactions().queue();
                                } else {
                                    message.editMessage("Processing...").queue();
                                    message.clearReactions().queue();
                                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                                    Guild g = r.getGuild();
                                    members.forEach(member -> {
                                        futures.add(g.removeRoleFromMember(member, r).submit());
                                    });
                                    CompletableFuture
                                        .allOf(futures.toArray(new CompletableFuture[0]))
                                        .handle((result, exception) -> {
                                            AtomicLong success = new AtomicLong(0);
                                            AtomicLong failed = new AtomicLong(0);
                                            futures.forEach(future -> {
                                                if (future.isCompletedExceptionally()) {
                                                    failed.getAndIncrement();
                                                } else {
                                                    success.incrementAndGet();
                                                }
                                            });
                                            message.editMessage("Removed from **" + success.get()
                                                + "** users. Could not remove from **" + failed
                                                .get()
                                                + "** users.").queue();
                                            return null;
                                        });
                                }
                            }, 10, TimeUnit.SECONDS);
                    });
            }
        }
    }

    @Command(name = "sync", parent = "option-role", clearance = 100)
    public void syncOptionRoles(Context context, CommandContext cmdContext) {
        // TODO: 11/13/19 Somehow provide some visibility into this
        ors.syncRoles();
        context.getChannel().sendMessage("Syncing option roles, this may take some time").queue();
    }


}
