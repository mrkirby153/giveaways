package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.EmptyMessageAction;
import com.mrkirby153.tgabot.IntegrationTest;
import com.mrkirby153.tgabot.TestUtils;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.OptionRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import net.dv8tion.jda.api.utils.Procedure;
import net.dv8tion.jda.internal.requests.EmptyRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@IntegrationTest
@Transactional
@Slf4j
class PollMessageManagerTest {

    public static final String TEST_HEADER = "!! %s !!";
    @MockBean
    private JDA jda;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private DiscordService discordService;

    private PollMessageService pms;
    private TextChannel textChannel;

    @BeforeEach
    void setup() {
        pms = new PollMessageManager(categoryRepository, jda, discordService, TEST_HEADER);
        textChannel = TestUtils.makeMockedTextChannel(TestUtils.GUILD, "poll-channel");
        when(jda.getTextChannelById(textChannel.getId())).thenReturn(textChannel);
    }

    @Test
    void updatePollMessage() {
        Category testCategory = makeCategory("Test Category", 2, "One", "Two", "Three");

        String expected = String.join("", Collections.nCopies(30, "─")) + "\n" +
            String.format(TEST_HEADER, testCategory.getName()) + "\n\n" +
            "* - One\n" +
            "* - Two\n" +
            "* - Three\n" +
            "\n\n**6 votes**\n\nVote by clicking the reactions below!";

        Message mockedMessage = Mockito.mock(
            Message.class);
        when(textChannel.sendMessage(anyString()))
            .thenReturn(new EmptyMessageAction(jda, mockedMessage));
        when(mockedMessage.getId()).thenReturn("121212");

        when(mockedMessage.addReaction(anyString())).thenReturn(new EmptyRestAction<>(jda, null));
        when(mockedMessage.addReaction(any(Emote.class)))
            .thenReturn(new EmptyRestAction<>(jda, null));

        pms.updatePollMessage(testCategory.getId());

        verify(textChannel).sendMessage(expected);
        verify(mockedMessage, times(3)).addReaction("*");

        assertThat(categoryRepository.findById(testCategory.getId())).get()
            .matches(category -> category.getMessage().equals(mockedMessage.getId()));

        // Test when message is already sent
        Category newCategory = makeCategory("Test Category", 2, "One", "Two", "Three");
        newCategory.setMessage("12345");
        Message existingMessage = mock(Message.class);
        when(existingMessage.getContentRaw()).thenReturn("");
        when(textChannel.retrieveMessageById("12345"))
            .thenReturn(new EmptyRestAction<>(jda, existingMessage));
        when(existingMessage.editMessage(anyString()))
            .thenReturn(new EmptyMessageAction(jda, existingMessage));
        pms.updatePollMessage(newCategory.getId());
        verify(existingMessage).editMessage(expected);

        // Test when message is already sent but does not exist
        Category thirdCategory = makeCategory("Third Category", 2, "One", "Two", "Three");
        thirdCategory.setMessage("6789");
        thirdCategory = categoryRepository.save(thirdCategory);
        when(textChannel.retrieveMessageById("6789")).thenReturn(new EmptyRestAction<>(jda, null));
        Message message = mock(Message.class);
        when(message.getId()).thenReturn("1");
        when(message.addReaction(anyString())).thenReturn(new EmptyRestAction<>(jda, null));
        when(message.addReaction(any(Emote.class))).thenReturn(new EmptyRestAction<>(jda, null));
        when(textChannel.sendMessage(anyString())).thenReturn(new EmptyMessageAction(jda, message));

        pms.updatePollMessage(thirdCategory.getId());
        verify(textChannel).sendMessage(expected);
        verify(message, times(3)).addReaction("*");
        assertThat(categoryRepository.findById(thirdCategory.getId())).get()
            .matches(category -> category.getMessage().equals(message.getId()));
    }

    @Test
    void updatePollMessageDebounced() {
        Category category = makeCategory("Test Category", 0, "One", "Two", "Three");
        Message message = mock(Message.class);
        when(textChannel.sendMessage(anyString())).thenReturn(new EmptyMessageAction(jda, message));
        when(message.addReaction(anyString())).thenReturn(new EmptyRestAction<>(jda, null));

        pms.updatePollMessageDebounced(category);
        pms.updatePollMessageDebounced(category);
        // We cant test updatePollMessage directly but we'll test this instead
        verify(jda).getTextChannelById(textChannel.getId());
    }

    @Test
    void getOutstandingVotes() throws InterruptedException, ExecutionException, TimeoutException {
        Message message = mock(Message.class);
        when(message.getId()).thenReturn("1234");
        Category category = makeCategory("Test Category", 1, "One");
        category.setMessage(message.getId());

        when(textChannel.retrieveMessageById(message.getId()))
            .thenReturn(new EmptyRestAction<>(jda, message));
        ReactionEmote reactionEmote = ReactionEmote.fromUnicode("%", jda);
        ReactionEmote emote = ReactionEmote.fromUnicode("*", jda);
        MessageReaction mockedMessageReaction = mock(MessageReaction.class);
        when(mockedMessageReaction.getReactionEmote()).thenReturn(emote);
        when(message.getReactions()).thenReturn(Arrays
            .asList(new MessageReaction(textChannel, reactionEmote, 1234, false, 1),
                mockedMessageReaction));

        when(mockedMessageReaction.retrieveUsers()).thenReturn(
            new EmptyReactionPaginationAction(jda, mockedMessageReaction,
                Collections.singletonList(TestUtils.TEST_USER1)));

        List<Vote> outstanding = pms.getOutstandingVotes(category).get(1, TimeUnit.SECONDS);
        assertThat(outstanding.size()).isEqualTo(1);
        assertThat(outstanding.get(0).getUser()).isEqualTo(TestUtils.TEST_USER1.getId());

        Category invalidCategory = new Category("Invalid Category", TestUtils.GUILD.getId(),
            "INVALID CHANNEL");
        invalidCategory.setMessage("12345");
        outstanding = pms.getOutstandingVotes(invalidCategory).get(1, TimeUnit.SECONDS);
        assertThat(outstanding.size()).isEqualTo(0);

        Category invalidCategoryMessage = new Category("Invalid Category", TestUtils.GUILD.getId(),
            textChannel.getId());
        invalidCategoryMessage.setMessage("9999");
        when(textChannel.retrieveMessageById("9999")).thenReturn(new EmptyRestAction<>(jda, null));
        outstanding = pms.getOutstandingVotes(invalidCategoryMessage).get(1, TimeUnit.SECONDS);
        assertThat(outstanding.size()).isEqualTo(0);

        Category invalidCategoryNotSent = new Category("INVALID CATEGORY", TestUtils.GUILD.getId(),
            textChannel.getId());
        outstanding = pms.getOutstandingVotes(invalidCategoryNotSent).get(1, TimeUnit.SECONDS);
        assertThat(outstanding.size()).isEqualTo(0);
    }

    @Test
    void updateReactions() {
        Message message = mock(Message.class);
        when(message.getId()).thenReturn("12345");

        Category category = new Category("Test Category", TestUtils.GUILD.getId(),
            textChannel.getId());
        category.setMessage(message.getId());
        Option option1 = new Option(category, false, "*", "Option 1");
        Option option2 = new Option(category, false, "+", "Option 2");
        option1 = optionRepository.save(option1);
        option2 = optionRepository.save(option2);
        category.getOptions().add(option1);
        category.getOptions().add(option2);
        category = categoryRepository.save(category);

        when(textChannel.retrieveMessageById(message.getId()))
            .thenReturn(new EmptyRestAction<>(jda, message));
        when(message.getReactions()).thenReturn(Collections.emptyList());
        when(message.addReaction(anyString())).thenReturn(new EmptyRestAction<>(jda, null));

        pms.updateReactions(category);

        verify(message).addReaction("*");
        verify(message).addReaction("+");
        reset(message);

        // Verify Extra Reaction
        when(message.addReaction(anyString())).thenAnswer(invocation -> {
            log.debug("message.addReaction()");
            return new EmptyRestAction<>(jda, null);
        });
        when(message.clearReactions()).thenAnswer(invocation -> {
            log.debug("message.clearReaction()");
            return new EmptyRestAction<>(jda, null);
        });
        when(message.getReactions()).thenReturn(Arrays.asList(
            new MessageReaction(textChannel, ReactionEmote.fromUnicode("$", jda), 12345, true, 1)));
        pms.updateReactions(category);

        verify(message).clearReactions();
        verify(message).addReaction("+");
        verify(message).addReaction("*");
    }

    @Test
    void removeCategory() {
        Category category = makeCategory("Test Category", 2, "One", "Two", "Three");
        category.setMessage("12345");
        category.setChannel(textChannel.getId());

        when(textChannel.deleteMessageById(any())).thenReturn(new EmptyRestAction<>(null));

        pms.removeCategory(category);

        verify(textChannel).deleteMessageById("12345");

        // Test invalid channel
        category.setChannel("INVALID");
        pms.removeCategory(category);
        reset(textChannel);
        verify(textChannel, times(0)).deleteMessageById(any());

        // Test no message
        category.setMessage(null);
        pms.removeCategory(category);
        reset(textChannel);
        verify(textChannel, times(0)).deleteMessageById(any());
    }

    private Category makeCategory(String name, int votesPerOption, String... options) {
        Category category = new Category(name, textChannel.getGuild().getId(), textChannel.getId());
        category = categoryRepository.save(category);

        for (String option : options) {
            Option e = new Option(category, false, "*", option);
            e = optionRepository.save(e);
            category.getOptions().add(e);
            for (int i = 0; i < votesPerOption; i++) {
                Vote v = new Vote(Integer.toString(i), e);
                voteRepository.save(v);
                e.getVotes().add(v);
            }
            optionRepository.save(e);
        }

        categoryRepository.save(category);

        // Verify the poll was created correctly
        Optional<Category> categoryOpt = categoryRepository.findById(category.getId());
        assertThat(categoryOpt).isPresent();
        Category retrievedCategory = categoryOpt.get();
        assertThat(retrievedCategory.getOptions().size()).isEqualTo(options.length);

        return category;
    }

    @AllArgsConstructor
    private static class EmptyReactionPaginationAction implements ReactionPaginationAction {

        private final JDA jda;
        private final MessageReaction reaction;
        private final List<User> users;

        @Nonnull
        @Override
        public MessageReaction getReaction() {
            return reaction;
        }

        @Nonnull
        @Override
        public ReactionPaginationAction skipTo(long id) {
            return this;
        }

        @Override
        public long getLastKey() {
            return 0;
        }

        @Nonnull
        @Override
        public ReactionPaginationAction setCheck(@Nullable BooleanSupplier checks) {
            return this;
        }

        @Override
        public int cacheSize() {
            return users.size();
        }

        @Override
        public boolean isEmpty() {
            return users.isEmpty();
        }

        @Nonnull
        @Override
        public List<User> getCached() {
            return users;
        }

        @Nonnull
        @Override
        public User getLast() {
            return users.get(users.size() - 1);
        }

        @Nonnull
        @Override
        public User getFirst() {
            return users.get(0);
        }

        @Nonnull
        @Override
        public ReactionPaginationAction limit(int limit) {
            return this;
        }

        @Nonnull
        @Override
        public ReactionPaginationAction cache(boolean enableCache) {
            return this;
        }

        @Override
        public boolean isCacheEnabled() {
            return false;
        }

        @Override
        public int getMaxLimit() {
            return 0;
        }

        @Override
        public int getMinLimit() {
            return 0;
        }

        @Override
        public int getLimit() {
            return 0;
        }

        @Nonnull
        @Override
        public CompletableFuture<List<User>> takeAsync(int amount) {
            return CompletableFuture.completedFuture(users);
        }

        @Nonnull
        @Override
        public CompletableFuture<List<User>> takeRemainingAsync(int amount) {
            return CompletableFuture.completedFuture(users);
        }

        @Nonnull
        @Override
        public CompletableFuture<?> forEachAsync(@Nonnull Procedure<? super User> action,
            @Nonnull Consumer<? super Throwable> failure) {
            users.forEach(action::execute);
            return CompletableFuture.completedFuture(null);
        }

        @Nonnull
        @Override
        public CompletableFuture<?> forEachRemainingAsync(@Nonnull Procedure<? super User> action,
            @Nonnull Consumer<? super Throwable> failure) {
            users.forEach(action::execute);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void forEachRemaining(@Nonnull Procedure<? super User> action) {
            users.forEach(action::execute);
        }

        @Nonnull
        @Override
        public PaginationIterator<User> iterator() {
            return new PaginationIterator<>(users, () -> null);
        }

        @Nonnull
        @Override
        public JDA getJDA() {
            return jda;
        }

        @Override
        public void queue(@Nullable Consumer<? super List<User>> success,
            @Nullable Consumer<? super Throwable> failure) {
            if (success != null) {
                success.accept(users);
            }
        }

        @Override
        public List<User> complete(boolean shouldQueue) throws RateLimitedException {
            return users;
        }

        @Nonnull
        @Override
        public CompletableFuture<List<User>> submit(boolean shouldQueue) {
            return CompletableFuture.completedFuture(users);
        }
    }
}