package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.ActionLog.ActionType;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.OptionRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import com.mrkirby153.tgabot.events.VoteCastEvent;
import com.mrkirby153.tgabot.services.ReactionService.Result;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
public class PollReactionManager implements PollReactionService {

    private final CategoryRepository categoryRepository;
    private final VoteRepository voteRepository;
    private final OptionRepository optionRepository;
    private final ReactionService reactionService;
    private final PollMessageService pollMessageService;
    private final VoteConfirmationService voteConfirmationService;
    private final LogService logService;
    private final ApplicationEventPublisher eventPublisher;
    private final JDA jda;

    private int reactionThreshold;

    private Set<Long> pendingOutstandingProcess = new CopyOnWriteArraySet<>();

    public PollReactionManager(@NonNull CategoryRepository cr,
        @NonNull ReactionService rs,
        @NonNull PollMessageService pms,
        @NonNull VoteRepository vr,
        @NonNull OptionRepository or,
        @NonNull VoteConfirmationService vs,
        @NonNull LogService als,
        @NonNull JDA jda,
        @NonNull ApplicationEventPublisher eventPublisher,
        @Value("${bot.reaction-threshold:10}") int reactionThreshold) {
        this.categoryRepository = cr;
        this.reactionService = rs;
        this.pollMessageService = pms;
        this.voteRepository = vr;
        this.optionRepository = or;
        this.reactionThreshold = reactionThreshold;
        this.voteConfirmationService = vs;
        this.logService = als;
        this.eventPublisher = eventPublisher;
        this.jda = jda;
    }

    @Override
    @Transactional
    public void onReact(MessageReactionAddEvent event) {
        if (event.getUser() == event.getJDA().getSelfUser()) {
            return; // Don't do anything for ourself
        }
        Optional<Category> categoryOpt = categoryRepository.findByMessage(event.getMessageId());
        if (!categoryOpt.isPresent()) {
            return;
        }
        Category category = categoryOpt.get();
        String reaction = event.getReactionEmote().isEmote() ? event.getReactionEmote().getId()
            : event.getReactionEmote().getEmoji();
        log.debug("Looking up {} on {}", reaction, category.getName());
        Optional<Option> opt = optionRepository.findByCategoryAndReaction(category, reaction);
        if (!opt.isPresent()) {
            return;
        }
        if (pendingOutstandingProcess.contains(category.getId())) {
            log.debug("Skipping processing of emote by {} on {} as the bot is still starting",
                event.getUser(), category.getName());
            return;
        }
        recordVote(category, opt.get(), event.getUser().getId(), true);
        event.getChannel().retrieveMessageById(event.getMessageId()).queue(msg -> {
            log.debug("There are {} pending removals. Threshold is {}",
                reactionService.pendingRemovals(msg), this.getThreshold());
            if (reactionService.pendingRemovals(msg) >= this.getThreshold()) {
                log.debug("Reaction has crossed pending threshold, removing");
                reactionService.removeAllReactions(msg).thenAccept(result -> {
                    log.debug("Reaction removal has completed with result {}", result);
                    if (result == Result.SUCCESS) {
                        log.debug("Re-adding reactions");
                        pollMessageService.updateReactions(category);
                    }
                });
            } else {
                if (event.getReactionEmote().isEmote()) {
                    log.debug("Removing custom emote by {} from {}", event.getUser(),
                        event.getMessageId());
                    reactionService
                        .removeReaction(msg, event.getUser(), event.getReactionEmote().getEmote());
                } else {
                    log.debug("Removing emote by {} from {}", event.getUser(),
                        event.getMessageId());
                    reactionService
                        .removeReaction(msg, event.getUser(), event.getReactionEmote().getEmoji());
                }
            }
        });
    }

    @Override
    public void recordVote(Category category, Option option, String userId, boolean updateMessage) {
        log.debug("Recording vote for {} on {}: {}", userId, category.getName(),
            option.getName());
        Optional<Vote> vote = voteRepository.findUserVote(userId, category);
        User user = jda.getUserById(userId);
        String logName = user != null ? LogManager.getLogName(user) : userId;
        Vote v;
        if (vote.isPresent()) {
            log.debug("Updating vote");
            v = vote.get();
            logService.recordAction(userId, ActionType.VOTE_RETRACT,
                String.format("**%s**", category.getName()));
            v.setOption(option);
            logService.recordAction(userId, ActionType.VOTE_CAST,
                String.format("**%s** - %s", category.getName(), option.getName()));
            voteRepository.saveAndFlush(v);
            logService.logMessage(
                logName + " has changed their vote on **" + category.getName()
                    + "**");
        } else {
            v = new Vote(userId, option);
            voteRepository.saveAndFlush(v);
            logService.recordAction(userId, ActionType.VOTE_CAST,
                String.format("**%s** - %s", category.getName(), option.getName()));
            logService.logMessage(
                logName + " has voted on **" + category.getName() + "**");
        }
        if (updateMessage) {
            pollMessageService.updatePollMessageDebounced(category);
        }
        if (user != null) {
            eventPublisher.publishEvent(new VoteCastEvent(user, v));
            voteConfirmationService.addVotedRoleToUser(user);
        }
    }

    @Override
    public void processOutstanding(Category category) {
        this.pendingOutstandingProcess.add(category.getId());
        pollMessageService.getOutstandingVotes(category).thenAccept(votes -> {
            try {
                log.debug("Category {} has {} outstanding votes", category.getName(), votes.size());
                votes
                    .forEach(vote -> recordVote(category, vote.getOption(), vote.getUser(), false));
                // Reset the poll reactions only if there were outstanding votes
                if (votes.size() > 0) {
                    resetReactions(category.getId());
                }
            } catch (Exception e) {
                log.error("Error processing outstanding", e);
            }
        });
    }

    @Override
    public int getThreshold() {
        return this.reactionThreshold;
    }

    @Override
    public void setThreshold(int threshold) {
        log.info("Setting reaction clear threshold to {}", threshold);
        this.reactionThreshold = threshold;
    }

    protected void resetReactions(long categoryId) {
        Optional<Category> repoCatOpt = categoryRepository.findById(categoryId);
        if (!repoCatOpt.isPresent()) {
            return;
        }
        Category repoCat = repoCatOpt.get();
        // We need to do some jank to egarly load up the options
        List<Option> options = optionRepository.findAllByCategory(repoCat);
        Category category = new Category(repoCat.getId(), repoCat.getName(), repoCat.getGuild(),
            repoCat
                .getChannel(), repoCat.getMessage(), options);

        Guild guild = jda.getGuildById(category.getGuild());
        if (guild != null) {
            TextChannel channel = guild.getTextChannelById(category.getChannel());
            if (channel != null) {
                channel.retrieveMessageById(category.getMessage()).queue(message -> {
                    message.clearReactions().queue(v -> {
                        pollMessageService.updateReactions(category);
                        log.debug("Poll reactions reset for {}", category.getName());
                        this.pendingOutstandingProcess.remove(category.getId());
                        pollMessageService.updatePollMessage(category.getId());
                    });
                });
            }
        }
    }

    @EventListener
    @Transactional
    public void onAppStart(ReadyEvent event) {
        log.info("Tallying votes while the bot was offline");
        logService.logMessage("Tallying votes missed while offline...");

        // Add all the categories to our do not process list
        List<Category> categories = new ArrayList<>();
        categoryRepository.findAll().forEach(category -> {
            category.getOptions().size();
            categories.add(category);
        });
        categories.stream().map(Category::getId).forEach(pendingOutstandingProcess::add);
        categories.forEach(this::processOutstanding);
    }
}
