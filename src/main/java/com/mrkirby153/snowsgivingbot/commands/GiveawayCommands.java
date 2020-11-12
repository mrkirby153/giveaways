package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayRoleEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.ConfirmationService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import com.mrkirby153.snowsgivingbot.services.PermissionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    private final PermissionService ps;
    private final ConfirmationService confirmationService;
    private final ShardManager shardManager;

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
        if (!context.getGuild().getSelfMember()
            .hasPermission(context.getTextChannel(), Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS)) {
            throw new CommandException(
                "I can't start a giveaway here due to missing permissions. Ensure that I have permission to add reactions and embed links!");
        }
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
                .createGiveaway(context.getTextChannel(), prizeStr, winners, time, secret,
                    context.getAuthor());
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }


    @Command(name = "end", arguments = {"<mid:snowflake>"}, clearance = 100)
    public void endGiveaway(Context context, CommandContext cmdContext) {
        try {
            GiveawayEntity entity = gr.findByMessageId(cmdContext.getNotNull("mid"))
                .orElseThrow(() -> new CommandException("Giveaway not found!"));
            giveawayService.endGiveaway(cmdContext.getNotNull("mid"));
            context.getChannel().sendMessage("Ended giveaway " + entity.getName()).queue();
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }

    @Command(name = "reroll", arguments = {"<mid:snowflake>", "[users:string...]"}, clearance = 100)
    public void reroll(Context context, CommandContext cmdContext) {
        GiveawayEntity entity = gr.findByMessageId(cmdContext.getNotNull("mid"))
            .orElseThrow(() -> new CommandException("Giveaway not found"));
        if (!entity.getGuildId().equals(context.getGuild().getId())) {
            throw new CommandException("Giveaway not found");
        }
        try {
            String[] users = null;
            String toReroll = cmdContext.get("users");
            if (toReroll != null) {
                users = toReroll.split(",");
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Are you sure you want to reroll **%s**?", entity.getName()));
            if (users != null && users.length > 0) {
                sb.append(" The following users will be rerolled:\n");
                for (String user : users) {
                    User u = shardManager.getUserById(user.trim());
                    if (u != null) {
                        sb.append(String
                            .format(" - **%s#%s** `%s`\n", u.getName(), u.getDiscriminator(),
                                u.getId()));
                    } else {
                        sb.append(String.format(" - `%s`\n", user.trim()));
                    }
                }
            } else {
              sb.append(" All users will be rerolled");
            }
            String[] finalUsers = users;
            context.getChannel().sendMessage(sb.toString()).submit()
                .thenCompose(msg -> confirmationService.confirm(msg, context.getAuthor()))
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        context.getChannel()
                            .sendMessage("An error occurred: " + throwable.getMessage()).queue();
                        return null;
                    }
                    if(result) {
                        context.getChannel().sendMessage("Rerolling giveaway...").queue();
                        giveawayService.reroll(entity.getMessageId(), finalUsers);
                    } else {
                        context.getChannel().sendMessage("Canceling").queue();
                    }
                    return null;
                });
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
        String[] winners = entity.getFinalWinners();

        String winnerString = Arrays.stream(winners).map(String::trim).map(s -> "<@!" + s + ">")
            .collect(Collectors.joining(", "));
        String winMessage = String
            .format("The winners for **%s** are\n%s", entity.getName(), winnerString);
        if (winMessage.length() < 2000) {
            context.getChannel().sendMessage(winMessage).queue();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("The winners for **%s** are\n", entity.getName()));
            for (String winner : winners) {
                String toAppend = String.format("<@!%s> ", winner);
                if (sb.length() + toAppend.length() > 1990) {
                    context.getChannel().sendMessage(sb.toString()).queue();
                    sb = new StringBuilder();
                }
                sb.append(toAppend);
            }
            if (sb.length() > 0) {
                context.getChannel().sendMessage(sb.toString()).queue();
            }
        }
    }

    @Command(name = "set", parent = "winners", arguments = {"<mid:snowflake>",
        "<winners:int>"}, clearance = 100)
    public void setWinners(Context context, CommandContext commandContext) {
        GiveawayEntity entity = gr.findByMessageId(commandContext.getNotNull("mid"))
            .orElseThrow(() -> new CommandException("Giveaway was not found"));
        if (entity.getState() != GiveawayState.RUNNING) {
            throw new CommandException("Giveaway is not running");
        }
        entity.setWinners(commandContext.getNotNull("winners"));
        gr.save(entity);
        giveawayService.update(entity);
        context.getChannel().sendMessage("Winners set to " + commandContext.getNotNull("winners"))
            .queue();
    }

    @Command(name = "add", arguments = {"<role:snowflake>"}, clearance = 100, parent = "role")
    public void addRole(Context context, CommandContext commandContext) {
        String roleId = commandContext.getNotNull("role");
        Role role = context.getGuild().getRoleById(roleId);
        if (role == null) {
            throw new CommandException(
                "The role with the id `" + roleId + "` was not found");
        }
        ps.addGiveawayRole(role);
        context.getChannel().sendMessage(
            "Added " + role.getName() + " as a giveaway role. They can now manage giveaways")
            .queue();
    }

    @Command(name = "remove", arguments = {"<role:snowflake>"}, clearance = 100, parent = "role")
    public void removeRole(Context context, CommandContext commandContext) {
        String roleId = commandContext.getNotNull("role");
        Role role = context.getGuild().getRoleById(roleId);
        if (role == null) {
            throw new CommandException(
                "The role with the id `" + roleId + "` was not found");
        }
        ps.removeGiveawayRole(role);
        context.getChannel().sendMessage("Removed " + role.getName() + " as a giveaway role")
            .queue();
    }

    @Command(name = "list", clearance = 100, parent = "role")
    public void listRoles(Context context, CommandContext commandContext) {
        List<GiveawayRoleEntity> roles = ps.getGiveawayRoles(context.getGuild());
        StringBuilder sb = new StringBuilder();
        sb.append("The following roles are configured on this server: ");
        sb.append(
            roles.stream().map(GiveawayRoleEntity::getRoleId).map(context.getJDA()::getRoleById)
                .filter(Objects::nonNull).map(Role::getName).collect(Collectors.joining(", ")));
        context.getChannel().sendMessage(sb).queue();
    }

    @Command(name = "export", clearance = 100)
    public void export(Context context, CommandContext commandContext) {
        List<GiveawayEntity> giveaways = giveawayService.getAllGiveaways(context.getGuild());
        if (giveaways.size() == 0) {
            throw new CommandException("No giveaways were found on the guild!");
        }

        StringBuilder csv = new StringBuilder();
        csv.append("id,name,channel,winners,final_winners\n");
        giveaways.forEach(giveawayEntity -> {
            csv.append(giveawayEntity.getId()).append(",").append("\"")
                .append(giveawayEntity.getName()).append("\",");
            TextChannel c = context.getJDA().getTextChannelById(giveawayEntity.getChannelId());
            if (c != null) {
                csv.append("\"").append(c.getName()).append(" (").append(c.getId()).append(")\",");
            } else {
                csv.append(giveawayEntity.getChannelId()).append(",");
            }
            csv.append(giveawayEntity.getWinners()).append(",").append("\"")
                .append(String.join(",", giveawayEntity.getFinalWinners())).append("\"\n");
        });
        context.getChannel().sendFile(csv.toString().getBytes(), "giveaways.csv").queue();
    }
}
