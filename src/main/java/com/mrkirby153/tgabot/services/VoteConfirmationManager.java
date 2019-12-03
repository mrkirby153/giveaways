package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.ActionLog.ActionType;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import com.mrkirby153.tgabot.utils.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.timing.Throttler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VoteConfirmationManager implements VoteConfirmationService {

    private static final String ZWS = "\u200B";
    private static final String MAILBOX = "\uD83D\uDCEB";

    private final JDA jda;
    private final String votedRoleId;
    private final String guildId;
    private final String votedMessage;
    private final String voteConfirmChannelName;
    private final VoteRepository voteRepository;
    private final CategoryRepository categoryRepository;
    private final LogService logService;

    /**
     * THe channel that the user is added to when they've voted
     */
    private String confirmationChannelId;
    /**
     * The throttler for throttling DM results
     */
    private final Throttler<User> dmThrottler = new Throttler<>(this::sendVoteDm, true);
    /**
     * The message id of the confirmation message
     */
    private String confirmationMessageId;

    public VoteConfirmationManager(JDA jda,
        LogService logService,
        @Value("${bot.voted.role}") String votedRole,
        @Value("${bot.guild}") String guild,
        @Value("${bot.voted.message:config/voted.txt}") String votedMessage,
        @Value("${bot.voted.channel:vote-confirmation}") String voteConfirmChannelName,
        VoteRepository voteRepository,
        CategoryRepository categoryRepository) {
        this.jda = jda;
        this.votedRoleId = votedRole;
        this.guildId = guild;
        this.voteRepository = voteRepository;
        this.votedMessage = votedMessage;
        this.voteConfirmChannelName = voteConfirmChannelName;
        this.categoryRepository = categoryRepository;
        this.logService = logService;
    }

    @Override
    public void addVotedRoleToUser(User user) {
        log.debug("Adding voted role to {}", user);
        Member member = getGuild().getMember(user);
        if (member == null) {
            log.debug("User is not in the server");
            return;
        }
        if (member.getRoles().contains(getVotedRole())) {
            log.debug("User is already in the voted role");
            return;
        }
        getGuild().addRoleToMember(member, getVotedRole()).queue(v -> {
            log.debug("Added voted role to {}", user);
        });
    }

    @Override
    public void removeVotedRoleFromUser(User user) {
        log.debug("Removing voted role from {}", user);
        Member member = getGuild().getMember(user);
        if (member == null) {
            log.debug("User is not in the server");
            return;
        }
        if (!member.getRoles().contains(getVotedRole())) {
            log.debug("User is not in the voted role");
            return;
        }
        getGuild().removeRoleFromMember(member, getVotedRole()).queue(v -> {
            log.debug("Removed voted role to {}", user);
        });
    }

    @Override
    public void syncVotedUsers() {
        log.info("Syncing voted users");
        List<String> votedUsers = voteRepository.getAllUserIds();

        // Add roles to members who have voted
        votedUsers.stream().map(uid -> getGuild().getMemberById(uid)).filter(Objects::nonNull)
            .filter(member -> !member.getRoles().contains(getVotedRole())).map(Member::getUser)
            .forEach(this::addVotedRoleToUser);

        // Remove roles from members who have not voted
        getGuild().getMembers().stream().filter(member -> !votedUsers.contains(member.getId()))
            .map(Member::getUser).forEach(this::removeVotedRoleFromUser);
        log.debug("Voted user sync complete");
    }

    @Override
    public void refreshVotedMessage() {
        log.info("Refreshing voted message");
        TextChannel channel = getGuild().getTextChannelById(confirmationChannelId);
        if (channel == null) {
            log.warn("Could not refresh voted message in channel that does not exist");
            return;
        }

        List<Message> messageHistory = new ArrayList<>();
        Message infoMessage = null;
        channel.getIterableHistory().forEach(messageHistory::add);
        List<Message> toDelete = new ArrayList<>();
        for (Message m : messageHistory) {
            if (m.getContentRaw().startsWith(ZWS + ZWS + ZWS)) {
                infoMessage = m;
                this.confirmationMessageId = m.getId();
                break;
            } else {
                toDelete.add(m);
            }
        }
        log.debug("Found message: {}", infoMessage);
        if (infoMessage == null) {
            channel.sendMessage(ZWS + ZWS + ZWS + getConfirmationMessage())
                .queue(m -> {
                    m.addReaction(MAILBOX).queue();
                    this.confirmationMessageId = m.getId();
                });
        } else {
            infoMessage.editMessage(ZWS + ZWS + ZWS + getConfirmationMessage()).queue();
            final Message finalInfoMessage = infoMessage;
            infoMessage.clearReactions().queue(v -> finalInfoMessage.addReaction(MAILBOX).queue());
        }
        log.debug("Purging deleted messages");
        CompletableFuture.allOf(channel.purgeMessages(toDelete).toArray(new CompletableFuture[0]))
            .thenRun(() -> log.debug("Purge complete"));

    }

    @EventListener
    public void onReady(ReadyEvent event) {
        log.info("Initializing");
        syncVotedUsers();
        makeVotedChannel();
        refreshVotedMessage();
    }

    @EventListener
    public void guildReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getUser() == jda.getSelfUser()) {
            return;
        }
        if (event.getMessageId().equals(confirmationMessageId)) {
            if (event.getReactionEmote().getName().equals(MAILBOX)) {
                log.debug("Vote confirmation message reaction added by {}", event.getUser());
                event.getReaction().removeReaction(event.getUser())
                    .queueAfter(500, TimeUnit.MILLISECONDS);
                dmThrottler.trigger(event.getUser(), 1, TimeUnit.MINUTES);
            }
        }
    }


    private Role getVotedRole() {
        return getGuild().getRoleById(this.votedRoleId);
    }

    private Guild getGuild() {
        return jda.getGuildById(this.guildId);
    }

    private TextChannel getConfirmationChannel() {
        return getGuild().getTextChannelById(this.confirmationChannelId);
    }

    private void makeVotedChannel() {
        List<TextChannel> potentialChannels = getGuild()
            .getTextChannelsByName(voteConfirmChannelName, true);
        TextChannel channel = potentialChannels.size() > 0 ? potentialChannels.get(0) : null;
        if (channel == null) {
            log.info("Creating voted channel");
            channel = getGuild().createTextChannel(voteConfirmChannelName).complete();
        }
        confirmationChannelId = channel.getId();
        log.info("Voted channel is {}", channel);

        // Create a channel override for us
        channel.getManager().putPermissionOverride(getGuild().getSelfMember(),
            combineRawPermissions(Permission.MESSAGE_WRITE, Permission.MANAGE_PERMISSIONS,
                Permission.MESSAGE_MANAGE), 0).
            // Create a channel override for the voted role
                putPermissionOverride(getVotedRole(),
                combineRawPermissions(Permission.MESSAGE_READ), 0)
            // Create a channel override for @everyone
            .putPermissionOverride(getGuild().getPublicRole(), 0,
                combineRawPermissions(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE)).queue();
    }

    private long combineRawPermissions(Permission... permissions) {
        long perms = 0;
        for (Permission p : permissions) {
            perms |= p.getRawValue();
        }
        return perms;
    }

    private String getConfirmationMessage() {
        try (FileInputStream fis = new FileInputStream(new File(this.votedMessage))) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Could not read confirmation message", e);
            return "<<ERROR READING CONFIRMATION MESSAGE: " + e.getMessage() + ">>";
        }
    }

    private void sendVoteDm(User user) {
        log.debug("Sending voted dm to {}", user);
        user.openPrivateChannel().queue(channel -> {
            StringBuilder sb = new StringBuilder();
            CompletableFuture<?> future = null;
            for (String line : buildVotes(user).split("\n")) {
                List<CompletableFuture<Message>> messageFutures = new ArrayList<>();
                if (sb.length() + line.length() > 2000) {
                    messageFutures.add(channel.sendMessage(sb.toString()).submit());
                    sb.setLength(0);
                } else {
                    sb.append(line).append('\n');
                }
                future = CompletableFuture
                    .allOf(messageFutures.toArray(new CompletableFuture[0]));
            }
            if (sb.length() > 0) {
                CompletableFuture<Message> f = channel.sendMessage(sb.toString()).submit();
                if (future == null) {
                    future = f;
                } else {
                    future = CompletableFuture.allOf(future, f);
                }
            }
            if (future != null) {
                future.handle((v, throwable) -> {
                    if (throwable != null) {
                        // An error occurred
                        handleDmThrowable(user, throwable);
                        return null;
                    } else {
                        logService.recordAction(user, ActionType.SEND_DM, "");
                        logService.logMessage(
                            ":mailbox_with_mail: Sent " + LogManager.getLogName(user)
                                + " their votes");
                        getConfirmationChannel()
                            .sendMessage(
                                user.getAsMention()
                                    + " I've sent a copy of your votes to your DMs!")
                            .queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS));
                    }
                    return null;
                });
            } else {
                handleDmThrowable(user, new IllegalStateException("Future is null"));
            }
        }, throwable -> handleDmThrowable(user, throwable));
    }


    protected String buildVotes(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello, ").append(user.getName())
            .append(" here are your responses for the polls\n\n");
        getVotes(user).forEach((option, vote) -> {
            String v = vote.isPresent() ? vote.get().getOption().getName() : "No vote";
            sb.append(String.format("**%s**: %s", option.getName(), v)).append('\n');
        });
        sb.append(
            "\n\nIf at any point you would like to change your vote, just click on the reactions again!");
        return sb.toString();
    }

    @Transactional
    protected Map<Category, Optional<Vote>> getVotes(User user) {
        Map<Category, Optional<Vote>> results = new HashMap<>();
        List<Category> categories = categoryRepository.findAll();
        List<Vote> userVotes = voteRepository.getAllByUser(user.getId());
        categories.forEach(category -> results.put(category,
            userVotes.stream().filter(v -> v.getOption().getCategory().getId() == category.getId())
                .findFirst()));
        return results;
    }

    private void handleDmThrowable(User user, Throwable throwable) {
        throwable = ExceptionUtils.unwrap(throwable);
        if (throwable instanceof ErrorResponseException) {
            ErrorResponseException exception = (ErrorResponseException) throwable;
            if (exception.getErrorCode() == ErrorResponse.CANNOT_SEND_TO_USER.getCode()) {
                getConfirmationChannel().sendMessage(user.getAsMention()
                    + " Please open your DMs!\n\nYou can open your DMs by right clicking on the server icon, clicking `Privacy Settings` and ensuring `Allow DMs from server members` is checked.")
                    .queue(msg -> msg.delete().queueAfter(30, TimeUnit.SECONDS));
            }
            logService.recordAction(user, ActionType.DM_FAILED, "DMs closed");
            return;
        }
        getConfirmationChannel().sendMessage(user.getAsMention()
            + " An unknown error occurred when sending your votes. Please contact a moderator for assistance")
            .queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS));
        log.error("An unknown exception occurred when DMing a user their votes", throwable);
        logService.recordAction(user, ActionType.DM_FAILED, throwable.getMessage());
        // Reset the throttler to 10 seconds so they can try again faster
        dmThrottler.update(user, 10, TimeUnit.SECONDS);

    }
}
