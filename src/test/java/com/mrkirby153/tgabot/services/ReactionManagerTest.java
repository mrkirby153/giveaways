package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.DelayedRestAction;
import com.mrkirby153.tgabot.TestUtils;
import com.mrkirby153.tgabot.services.ReactionManager.QueuedReactionTask;
import com.mrkirby153.tgabot.services.ReactionService.Result;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactionManagerTest {

    @MockBean
    private JDA jda;

    private ReactionService reactionManager;


    @BeforeEach
    public void setUp() {
        reactionManager = new ReactionManager();
    }

    @Test
    void removeReaction() {
        Message message = mock(Message.class);
        MessageReaction reaction = mock(MessageReaction.class);
        when(message.getId()).thenReturn("12345");
        when(reaction.getReactionEmote()).thenReturn(ReactionEmote.fromUnicode("*", jda));
        when(message.getReactions())
            .thenReturn(Collections.singletonList(reaction));

        DelayedRestAction<Void> removeReactionRestAction = new DelayedRestAction<>(jda, null);
        when(reaction.removeReaction(any())).thenReturn(removeReactionRestAction);

        reactionManager.removeReaction(message, TestUtils.TEST_USER1, "*");
        assertThat(reactionManager.pendingRemovals(message)).isEqualTo(1);

        removeReactionRestAction.trigger();
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> reactionManager.pendingRemovals(message) == 0);

        DelayedRestAction.waitForRestActionTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void removeAllReactions() throws InterruptedException, ExecutionException, TimeoutException {
        Message message = mock(Message.class);
        MessageReaction reaction = mock(MessageReaction.class);
        when(message.getId()).thenReturn("12345");
        when(reaction.getReactionEmote()).thenReturn(ReactionEmote.fromUnicode("*", jda));

        when(message.getReactions())
            .thenReturn(Collections.singletonList(reaction));

        DelayedRestAction<Void> removeReactionRestAction = new DelayedRestAction<>(jda, null);
        when(reaction.removeReaction(any())).thenReturn(removeReactionRestAction);

        CompletableFuture<Result> cf = reactionManager
            .removeReaction(message, TestUtils.TEST_USER1, "*");
        assertThat(reactionManager.pendingRemovals(message)).isEqualTo(1);

        reactionManager.removeAllReactions(message);
        assertThat(cf.get(1, TimeUnit.SECONDS)).isEqualTo(Result.ABORTED);

        CompletableFuture<Result> result = reactionManager.removeAllReactions(message);
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(Result.NO_OP);
    }

    @Test
    void clearFinishedTasks() {
        Map<QueuedReactionTask, Future> tasks = new ConcurrentHashMap<>();
        for (int i = 0; i < 3; i++) {
            QueuedReactionTask qrt = mock(QueuedReactionTask.class);
            when(qrt.getFuture()).thenReturn(CompletableFuture.completedFuture(Result.SUCCESS));
            when(qrt.getMessageId()).thenReturn("12345");
            tasks.put(qrt, mock(Future.class));
        }
        ReflectionTestUtils.setField(reactionManager, "runningTasks", tasks);

        Message message = mock(Message.class);
        when(message.getId()).thenReturn("12345");
        assertThat(reactionManager.pendingRemovals(message)).isEqualTo(3L);

        ((ReactionManager) reactionManager).clearFinishedTasks();

        assertThat(reactionManager.pendingRemovals(message)).isEqualTo(0);
    }
}