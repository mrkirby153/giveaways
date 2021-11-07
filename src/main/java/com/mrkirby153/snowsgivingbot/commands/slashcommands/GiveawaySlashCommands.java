package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.slashcommand.SlashCommand;
import com.mrkirby153.botcore.command.slashcommand.SlashCommandParameter;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayRoleEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
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
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Component
@AllArgsConstructor
@Slf4j
public class GiveawaySlashCommands {

    private final GiveawayService giveawayService;
    private final GiveawayRepository gr;
    private final PermissionService ps;
    private final ConfirmationService confirmationService;

    @SlashCommand(name = "start", description = "Starts a new giveaway", clearance = 100)
    public void start(SlashCommandEvent event,
        @SlashCommandParameter(name = "duration", description = "How long the giveaway is to last (i.e. 3h)") String duration,
        @SlashCommandParameter(name = "prize", description = "The prize to give away") String prize,
        @SlashCommandParameter(name = "winners", description = "The amount of winners (default 1)") @Nullable Integer winners,
        @SlashCommandParameter(name = "channel", description = "The channel to run the giveaway in (defaults to the current channel)")
        @Nullable TextChannel textChannel,
        @SlashCommandParameter(name = "host", description = "The host of the giveaway (defaults to you)")
        @Nullable User host) {
        if (!event.isFromGuild()) {
            throw new CommandException("Giveaways can only be started in guilds");
        }
        if (winners == null) {
            winners = 1;
        }
        if (textChannel == null) {
            // Yes this @Nonnull method can return null (i.e. in a thread) thanks Java....
            if (event.getChannel() != null) {
                textChannel = event.getTextChannel();
            } else {
                throw new CommandException("Cannot start a giveaway in this channel!");
            }
        }
        if (host == null) {
            host = event.getUser();
        }
        if (event.getGuild() != null && !event.getGuild().getSelfMember()
            .hasPermission(textChannel, Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS)) {
            throw new CommandException("I can't start a giveawy in " + textChannel.getAsMention()
                + " due to missing permissions. Ensure that I have permissions to add reactions and embed links!");
        }
        try {
            giveawayService.createGiveaway(textChannel, prize, winners, duration, false, host);
            event.reply("Giveaway has been created in " + textChannel.getAsMention())
                .setEphemeral(true).queue();
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }

    @SlashCommand(name = "end", description = "Ends a giveaway", clearance = 100)
    public void endGiveaway(SlashCommandEvent event,
        @SlashCommandParameter(name = "message_id", description = "The message id of the giveaway to end") String messageId) {
        GiveawayEntity entity = gr.findByMessageId(messageId)
            .orElseThrow(() -> new CommandException("Giveaway not found"));
        giveawayService.endGiveaway(messageId);
        event.reply("Ended giveaway **" + entity.getName() + "**").queue();
    }

    @SlashCommand(name = "reroll", description = "Rerolls the winners of a giveaway", clearance = 100)
    public void reroll(SlashCommandEvent event,
        @SlashCommandParameter(name = "message_id", description = "The message id of the giveaway to reroll") String messageId,
        @SlashCommandParameter(name = "users", description = "A list of user ids to reroll (comma separated)") @Nullable String userIds) {
        GiveawayEntity entity = gr.findByMessageId(messageId)
            .orElseThrow(() -> new CommandException("Giveaway not found"));
        event.deferReply().queue(hook -> {
            try {
                String[] users = null;
                if (userIds != null) {
                    users = userIds.split(",");
                }
                StringBuilder sb = new StringBuilder();
                sb.append(
                    String.format("Are you sure you want to reroll **%s**?", entity.getName()));
                if (users != null && users.length > 0) {
                    sb.append(" The following users will be rerolled:\n");
                    for (String user : users) {
                        sb.append(String.format(" - `%s`", user));
                    }
                } else {
                    sb.append(" All users will be rerolled");
                }
                String[] finalUsers = users;
                event.getChannel().sendMessage(sb.toString()).submit()
                    .thenCompose(msg -> {
                        hook.deleteOriginal().queue();
                        return confirmationService.confirm(msg, event.getUser());
                    }).handle((result, throwable) -> {
                        if (throwable != null) {
                            event.getChannel()
                                .sendMessage("An error occurred: " + throwable.getMessage())
                                .queue();
                            return null;
                        }
                        if (result) {
                            event.getChannel().sendMessage("Rerolling giveaway...").queue();
                            giveawayService.reroll(entity.getMessageId(), finalUsers);
                        } else {
                            event.getChannel().sendMessage("Canceled").queue();
                        }
                        return null;
                    });
            } catch (IllegalArgumentException | IllegalStateException e) {
                hook.editOriginal(":no_entry: " + e.getMessage()).queue();
            }
        });
    }

    @SlashCommand(name = "winners get", description = "Gets the winners of a giveaway", clearance = 100)
    public void getWinners(SlashCommandEvent event,
        @SlashCommandParameter(name = "message_id", description = "The message id of the giveaway") String messageId,
        @SlashCommandParameter(name = "private", description = "If the winners should be displayed only to you (Default: false)") @Nullable Boolean isPrivate) {
        if (isPrivate == null) {
            isPrivate = false;
        }
        GiveawayEntity entity = gr.findByMessageId(messageId)
            .orElseThrow(() -> new CommandException("Giveaway was not found"));
        if (entity.getState() != GiveawayState.ENDED) {
            throw new CommandException("Giveaway has not ended yet");
        }
        String[] winners = entity.getFinalWinners();
        String winnerString = Arrays.stream(winners).map(String::trim).map(s -> "<@!" + s + ">")
            .collect(Collectors.joining(", "));
        String winMessage = String
            .format("The winners for **%s** are\n%s", entity.getName(), winnerString);
        if (winMessage.length() < 2000) {
            event.reply(winMessage).setEphemeral(isPrivate).queue();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("The winners for **%s** are\n", entity.getName()));
            boolean firstMessage = true;
            for (String winner : winners) {
                String toAppend = String.format("<@!%s> ", winner);
                if (sb.length() + toAppend.length() > 1990) {
                    if (firstMessage) {
                        event.reply(sb.toString()).setEphemeral(isPrivate).queue();
                        firstMessage = false;
                    } else {
                        event.getHook().sendMessage(sb.toString()).setEphemeral(isPrivate).queue();
                    }
                }
                sb.append(toAppend);
            }
            if (sb.length() > 0) {
                if (firstMessage) {
                    event.reply(sb.toString()).setEphemeral(isPrivate).queue();
                } else {
                    event.getHook().sendMessage(sb.toString()).setEphemeral(isPrivate).queue();
                }
            }
        }
    }

    @SlashCommand(name = "winners set", description = "Sets the number of winners for a giveaway", clearance = 100)
    public void setWinners(SlashCommandEvent event,
        @SlashCommandParameter(name = "message_id", description = "The message id of the gveaway") String messageId,
        @SlashCommandParameter(name = "winners", description = "The number of winners") int winners) {
        GiveawayEntity entity = gr.findByMessageId(messageId)
            .orElseThrow(() -> new CommandException("Giveaway was not found"));
        if (!entity.getGuildId().equals(event.getGuild().getId())) {
            throw new CommandException("Giveaway was not found");
        }
        if (entity.getState() != GiveawayState.RUNNING) {
            throw new CommandException("Giveaway is not running");
        }
        entity.setWinners(winners);
        gr.save(entity);
        giveawayService.update(entity);
        event.reply("Winners set to " + winners).setEphemeral(true).queue();
    }

    @SlashCommand(name = "role add", description = "Adds a role as a giveaway manager", clearance = 100)
    public void addRole(SlashCommandEvent event,
        @SlashCommandParameter(name = "role", description = "The role") Role role) {
        ps.addGiveawayRole(role);
        event.reply("Added " + role.getAsMention()
            + " as a giveaway role. Users with this role can now manage giveaways").allowedMentions(
            Collections.emptyList()).queue();
    }

    @SlashCommand(name = "role remove", description = "Removes a role as a giveaway manager", clearance = 100)
    public void removeRole(SlashCommandEvent event,
        @SlashCommandParameter(name = "role", description = "The role") Role role) {
        ps.removeGiveawayRole(role);
        event.reply("Removed " + role.getAsMention() + " from giveaway roles")
            .allowedMentions(Collections.emptyList()).queue();
    }

    @SlashCommand(name = "role list", description = "Lists all roles configured as giveaway managers", clearance = 100)
    public void listRoles(SlashCommandEvent event) {
        List<GiveawayRoleEntity> roles = ps.getGiveawayRoles(event.getGuild());
        StringBuilder sb = new StringBuilder();
        sb.append("The following roles are configured on this server: ");
        sb.append(roles.stream().map(GiveawayRoleEntity::getRoleId).map(event.getJDA()::getRoleById)
            .filter(Objects::nonNull).map(Role::getAsMention).collect(Collectors.joining(", ")));
        event.reply(sb.toString()).allowedMentions(Collections.emptyList()).queue();
    }

    @SlashCommand(name = "export", description = "Exports a list of all giveaways as a CSV", clearance = 100)
    public void export(SlashCommandEvent event) {
        event.deferReply().queue(hook -> {
            List<GiveawayEntity> giveaways = giveawayService.getAllGiveaways(event.getGuild());
            if (giveaways.size() == 0) {
                throw new CommandException("No giveaways were found on the guild!");
            }

            StringBuilder csv = new StringBuilder();
            csv.append("id,name,channel,winners,final_winners\n");
            giveaways.forEach(giveawayEntity -> {
                csv.append(giveawayEntity.getId()).append(",").append("\"")
                    .append(giveawayEntity.getName()).append("\",");
                TextChannel c = event.getJDA().getTextChannelById(giveawayEntity.getChannelId());
                if (c != null) {
                    csv.append("\"").append(c.getName()).append(" (").append(c.getId())
                        .append(")\",");
                } else {
                    csv.append(giveawayEntity.getChannelId()).append(",");
                }
                csv.append(giveawayEntity.getWinners()).append(",").append("\"")
                    .append(String.join(",", giveawayEntity.getFinalWinners())).append("\"\n");
            });
            event.getChannel().sendFile(csv.toString().getBytes(), "giveaways.csv")
                .queue(m -> hook.deleteOriginal().queue());
        });
    }
}
