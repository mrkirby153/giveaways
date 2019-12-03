package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.DelayedRestAction;
import com.mrkirby153.tgabot.IntegrationTest;
import com.mrkirby153.tgabot.TestUtils;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.OptionRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import com.mrkirby153.tgabot.services.ReactionService.Result;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.internal.requests.EmptyRestAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@IntegrationTest
class PollReactionManagerTest {

    @MockBean
    private JDA jda;

    @MockBean
    private ReactionService rs;
    @MockBean
    private PollMessageService pms;
    @MockBean
    private VoteConfirmationService vcs;

    @Autowired
    private PollReactionService prs;
    @Autowired
    private OptionRepository optionRepo;
    @Autowired
    private CategoryRepository categoryRepo;
    @Autowired
    private VoteRepository voteRepo;

    private TextChannel channel;
    private Option option;
    private Option option2;
    private Category category;


    @BeforeEach
    void setUp() {
        channel = TestUtils.makeMockedTextChannel(TestUtils.GUILD, "test-channel");
        category = new Category("Test Category", TestUtils.GUILD.getId(), channel.getId());
        category.setMessage("1337");
        category = categoryRepo.save(category);
        option = optionRepo.save(new Option(category, false, "*", "Testing"));
        option2 = optionRepo.save(new Option(category, false, "+", "Testing"));

        SelfUser su = mock(SelfUser.class);
        when(su.getName()).thenReturn("SELF USER");
        when(su.getDiscriminator()).thenReturn("0000");
        when(su.getId()).thenReturn("1234567890");
        when(su.isBot()).thenReturn(true);
        when(jda.getSelfUser()).thenReturn(su);
    }

    @AfterEach
    void tearDown() {
        optionRepo.deleteAll();
        categoryRepo.deleteAll();
        voteRepo.deleteAll();
    }

    @Test
    void onReact() {
        MessageReactionAddEvent event = mock(MessageReactionAddEvent.class);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(TestUtils.TEST_USER1);
        when(event.getMessageId()).thenReturn("1337");
        when(event.getReactionEmote()).thenReturn(ReactionEmote.fromUnicode("*", jda));
        when(event.getChannel()).thenReturn(channel);

        Message message = mock(Message.class);
        when(channel.retrieveMessageById(anyString()))
            .thenReturn(new EmptyRestAction<>(jda, message));
        when(rs.pendingRemovals(any())).thenReturn(0L);

        prs.onReact(event);
        assertThat(voteRepo.findUserVote(TestUtils.TEST_USER1.getId(), category)).map(
            v -> v.getOption().getId()).get().isEqualTo(option.getId());
        verify(rs).removeReaction(message, TestUtils.TEST_USER1, "*");

        // Test updating vote
        when(event.getReactionEmote()).thenReturn(ReactionEmote.fromUnicode("+", jda));
        prs.onReact(event);
        assertThat(voteRepo.findUserVote(TestUtils.TEST_USER1.getId(), category))
            .map(v -> v.getOption().getId()).get().isEqualTo(option2.getId());

        // Test vote with high threshold
        when(rs.pendingRemovals(any())).thenReturn(prs.getThreshold() + 5L);
        when(rs.removeAllReactions(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.SUCCESS));
        prs.onReact(event);
        verify(rs).removeAllReactions(message);
        verify(pms).updateReactions(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testVotePendingProcess() {
        MessageReactionAddEvent event = mock(MessageReactionAddEvent.class);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(TestUtils.TEST_USER1);
        when(event.getMessageId()).thenReturn("1337");
        when(event.getReactionEmote()).thenReturn(ReactionEmote.fromUnicode("*", jda));
        when(event.getChannel()).thenReturn(channel);

        ((Set) ReflectionTestUtils.getField(prs, "pendingOutstandingProcess"))
            .add(category.getId());

        Message message = mock(Message.class);
        when(channel.retrieveMessageById(anyString()))
            .thenReturn(new EmptyRestAction<>(jda, message));
        when(rs.pendingRemovals(any())).thenReturn(0L);

        prs.onReact(event);

        assertThat(voteRepo.findUserVote(TestUtils.TEST_USER1.getId(), category)).isNotPresent();
    }

    @Test
    void recordVote() {
        when(jda.getUserById(TestUtils.TEST_USER1.getId())).thenReturn(TestUtils.TEST_USER1);
        prs.recordVote(category, option, TestUtils.TEST_USER1.getId(), false);
        assertThat(voteRepo.findUserVote(TestUtils.TEST_USER1.getId(), category))
            .map(v -> v.getOption().getId()).get().isEqualTo(option.getId());

        verify(vcs).addVotedRoleToUser(TestUtils.TEST_USER1);

        prs.recordVote(category, option2, TestUtils.TEST_USER1.getId(), true);
        assertThat(voteRepo.findUserVote(TestUtils.TEST_USER1.getId(), category))
            .map(v -> v.getOption().getId()).get().isEqualTo(option2.getId());

        verify(pms).updatePollMessageDebounced(category);
    }

    @Test
    void processOutstanding() throws InterruptedException {
        when(pms.getOutstandingVotes(category)).thenReturn(CompletableFuture.completedFuture(
            Collections.singletonList(new Vote("1234", option))));
        when(jda.getGuildById(TestUtils.GUILD.getId())).thenReturn(TestUtils.GUILD);

        TextChannel textChannel = mock(TextChannel.class);
        when(TestUtils.GUILD.getTextChannelById(category.getChannel())).thenReturn(textChannel);

        Message message = mock(Message.class);
        when(textChannel.retrieveMessageById(category.getMessage()))
            .thenReturn(new EmptyRestAction<>(jda, message));
        DelayedRestAction<Void> clearReactionsRA = new DelayedRestAction<>(jda, null);
        when(message.clearReactions()).thenReturn(clearReactionsRA);

        prs.processOutstanding(category);

        assertThat(((Set) ReflectionTestUtils.getField(prs, "pendingOutstandingProcess"))
            .contains(category.getId())).isTrue();
        assertThat(voteRepo.findUserVote("1234", category)).map(v -> v.getOption().getId()).get()
            .isEqualTo(option.getId());

        clearReactionsRA.trigger();
        await().atMost(1, TimeUnit.SECONDS)
            .until(() -> !((Set) ReflectionTestUtils.getField(prs, "pendingOutstandingProcess"))
                .contains(category.getId()));
        verify(pms).updateReactions(any(Category.class));
        verify(pms).updatePollMessage(category.getId());
    }
}