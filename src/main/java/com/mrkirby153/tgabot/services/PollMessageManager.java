package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.timing.Debouncer;
import me.mrkirby153.kcutils.timing.Debouncer.Mode;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PollMessageManager implements PollMessageService {

    private static final String DEFAULT_POLL_HEADER = "**==[ %s ]==**";

    private final CategoryRepository categoryRepo;
    private final JDA jda;
    private final DiscordService discordService;
    private final String header;
    private final Debouncer<Category> debouncer = new Debouncer<>(category -> {
        if (category != null) {
            updatePollMessage(category);
        }
    }, null, Mode.BOTH);

    public PollMessageManager(CategoryRepository categoryRepository, JDA jda,
        DiscordService discordService,
        @Value("${poll.header:" + DEFAULT_POLL_HEADER + "}") String header) {
        this.categoryRepo = categoryRepository;
        this.discordService = discordService;
        this.jda = jda;
        this.header = header;
    }

    @Override
    public void updatePollMessage(Category category) {
        log.debug("Updating message for category {}", category.getId());
        final StringBuilder builder = new StringBuilder();
        builder.append(String.join("", Collections.nCopies(30, "─"))).append('\n');
        builder.append(String.format(header, category.getName())).append("\n\n");
        long totalVotes = 0;
        if (category.getOptions() != null) {
            category.getOptions().forEach(option -> {
                String emote =
                    option.isCustom() ? discordService.findEmoteById(option.getReaction())
                        .getAsMention() : option.getReaction();
                builder.append(emote).append(" - ").append(option.getName()).append('\n');
            });

            totalVotes = category.getOptions().stream().mapToLong(opt -> {
                if (opt.getVotes() != null) {
                    return opt.getVotes().size();
                } else {
                    return 0;
                }
            }).sum();
        }

        builder.append("\n\n**").append(totalVotes)
            .append(" votes**\n\nVote by clicking the reactions below!");

        String message = builder.toString();

        TextChannel chan = jda.getTextChannelById(category.getChannel());
        if (chan == null) {
            log.warn("Attempting to update a category ({}) in a non-existent channel {}. Ignoring",
                category.getId(), category.getChannel());
            return;
        }

        if (category.getMessage() == null) {
            chan.sendMessage(message).queue(m -> {
                addOptionsReactions(category, m);
                category.setMessage(m.getId());
                categoryRepo.save(category);
            });
        } else {
            chan.retrieveMessageById(category.getMessage()).queue(discordMessage -> {
                if (discordMessage == null) {
                    // If the message got deleted, send a new one
                    log.debug("Message for ({}) was deleted. Re-sending a new one",
                        category.getId());
                    chan.sendMessage(message).queue(m -> {
                        addOptionsReactions(category, m);
                        category.setMessage(m.getId());
                        categoryRepo.save(category);
                    });
                    return;
                }
                if (!discordMessage.getContentRaw().equals(message)) {
                    discordMessage.editMessage(message).queue();
                } else {
                    log.debug("Before and after messages are the same. Skipping");
                }
            });
        }
    }

    @Override
    public void updatePollMessage(long categoryId) {
        Optional<Category> cat = categoryRepo.findById(categoryId);
        if (cat.isPresent()) {
            updatePollMessage(cat.get());
        } else {
            throw new IllegalArgumentException("Category " + categoryId + " not found");
        }
    }

    @Override
    public void updatePollMessageDebounced(Category category) {
        debouncer.debounce(category, 1, TimeUnit.MINUTES);
    }

    @Override
    public CompletableFuture<List<Vote>> getOutstandingVotes(Category category) {
        List<Vote> outstanding = new ArrayList<>();
        CompletableFuture<List<Vote>> cf = new CompletableFuture<>();
        if (category.getMessage() == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        TextChannel channel = jda.getTextChannelById(category.getChannel());
        if (channel == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        channel.retrieveMessageById(category.getMessage()).queue(message -> {
            if (message == null) {
                cf.complete(Collections.emptyList());
                return;
            }
            Map<String, Option> validOptions = new HashMap<>();
            category.getOptions().forEach(option -> {
                validOptions.put(option.getReaction(), option);
            });
            message.getReactions().forEach(messageReaction -> {
                ReactionEmote re = messageReaction.getReactionEmote();
                if ((re.isEmote() && validOptions.containsKey(re.getEmote().getId())) || (
                    re.isEmoji()
                        && validOptions.containsKey(re.getEmoji()))) {
                    messageReaction.retrieveUsers().forEachAsync(user -> {
                        if (user == jda.getSelfUser()) {
                            return true;
                        }
                        String key = re.isEmoji() ? re.getEmoji() : re.getEmote().getId();
                        outstanding.add(new Vote(user.getId(), validOptions.get(key)));
                        return true;
                    }).thenAccept(v -> {
                        cf.complete(outstanding);
                    });
                }
            });
        });
        return cf;
    }

    @Override
    public void updateReactions(Category category) {
        log.debug("Updating reactions on {}", category.getId());
        Set<String> existingReactions = new HashSet<>();

        if (category.getMessage() == null) {
            // Message has not been sent
            return;
        }

        TextChannel channel = jda.getTextChannelById(category.getChannel());
        if (channel == null) {
            return;
        }

        channel.retrieveMessageById(category.getMessage()).queue(message -> {
            if (message == null) {
                // Message has been deleted
                return;
            }
            message.getReactions().forEach(reaction -> {
                ReactionEmote reactionEmote = reaction.getReactionEmote();
                existingReactions
                    .add(
                        reactionEmote.isEmote() ? reactionEmote.getId() : reactionEmote.getEmoji());
            });

            List<String> optionReactions = category.getOptions().stream().map(Option::getReaction)
                .collect(
                    Collectors.toList());
            boolean shouldAdd = category.getOptions().stream()
                .anyMatch(option -> !existingReactions.contains(option.getReaction()));
            boolean shouldRemove = existingReactions.stream()
                .anyMatch(reaction -> !optionReactions.contains(reaction));

            log.debug("shouldRemove = {}, shouldAdd = {}", shouldRemove, shouldAdd);
            if (shouldRemove) {
                message.clearReactions().queue(v -> addOptionsReactions(category, message));
                return;
            }
            if (shouldAdd) {
                addOptionsReactions(category, message);
            }
        });
    }

    @Override
    public void removeCategory(Category category) {
        if (category.getMessage() == null) {
            return; // No-op
        }
        TextChannel channel = jda.getTextChannelById(category.getChannel());
        if (channel == null) {
            return; // No-op
        }
        channel.deleteMessageById(category.getMessage()).queue();
        category.setMessage(null);
    }


    private void addOptionsReactions(Category category, Message message) {
        if (category.getOptions() != null) {
            category.getOptions().forEach(option -> {
                if (option.isCustom()) {
                    message.addReaction(discordService.findEmoteById(option.getReaction())).queue();
                } else {
                    message.addReaction(option.getReaction()).queue();
                }
            });
        }
    }
}
