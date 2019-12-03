package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.TestUtils;
import com.mrkirby153.tgabot.entity.ActionLog;
import com.mrkirby153.tgabot.entity.ActionLog.ActionType;
import com.mrkirby153.tgabot.entity.repo.ActionRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
class LogManagerTest {

    @Autowired
    private ActionRepository actionRepository;

    @MockBean
    private JDA jda;

    private LogService logService;

    @Mock
    private TextChannel logChannel;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(logChannel.getId()).thenReturn("12345");
        when(jda.getTextChannelById("12345")).thenReturn(logChannel);

        logService = new LogManager(actionRepository, jda, logChannel.getId());
    }

    @Test
    void getLogName() {
        assertThat(LogManager.getLogName(TestUtils.TEST_USER1)).isEqualTo(
            TestUtils.TEST_USER1.getName() + " (`" + TestUtils.TEST_USER1.getId() + "`)");
    }

    @Test
    void recordAction() {
        ActionLog al = logService
            .recordAction(TestUtils.TEST_USER1, ActionType.VOTE_CAST, "Test vote cast");
        assertThat(actionRepository.findById(al.getId())).get().isEqualTo(al);
    }

    @Test
    void getActions() {
        List<ActionLog> actions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ActionLog log = new ActionLog(TestUtils.TEST_USER1.getId(), ActionType.VOTE_CAST);
            log.setData("Cast vote " + i);
            actions.add(actionRepository.save(log));
        }

        Page<ActionLog> data = logService
            .getActions(TestUtils.TEST_USER1.getId(), Pageable.unpaged());
        assertThat(actions).isEqualTo(data.getContent());
    }

    @Test
    void logMessage() throws Exception {
        MessageAction mockMessageAction = mock(MessageAction.class);
        when(logChannel.sendMessage(anyString())).thenReturn(mockMessageAction);
        String testMessage = "Test Message";

        logService.logMessage(testMessage);

        List<String> pending = (List<String>) ReflectionTestUtils
            .getField(logService, "pendingMessages");
        assertThat(pending).isNotNull();
        assertThat(pending.size()).isNotZero();
        assertThat(pending.get(0)).contains("Test Message");

        runWithLogPump(() -> {
            waitForMessageToBeLogged();
            verify(logChannel).sendMessage(anyString());
        });

        // Test behaviour when ratelimited
        runWithLogPump(() -> {
            MessageAction action = mock(MessageAction.class);
            try {
                when(action.complete(false)).thenThrow(new RateLimitedException("", 0));
            } catch (RateLimitedException e) {
                throw new RuntimeException("This should never happen", e);
            }
            when(logChannel.sendMessage(anyString())).thenReturn(action);

            logService.logMessage("Testing");
            waitForMessageToBeLogged();
            assertThat(ReflectionTestUtils.getField(logService, "quietPeriod")).isNotEqualTo(-1);

            ReflectionTestUtils
                .setField(logService, "quietPeriod", System.currentTimeMillis());
            await().atMost(1, TimeUnit.MINUTES)
                .until(() -> (Long) ReflectionTestUtils.getField(logService, "quietPeriod") == -1L);
        });
    }

    @Test
    void testLogMessageTooLong() {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            message.append("A");
        }
        assertThatThrownBy(() -> logService.logMessage(message.toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Provided message was too long");
    }


    private void runWithLogPump(Runnable runnable) throws Exception {
        try {
            ((LogManager) logService).onReady(null);
            runnable.run();
        } finally {
            ((LogManager) logService).destroy();
            await().atMost(5, TimeUnit.SECONDS).until(() -> {
                Thread logPumpThread = (Thread) ReflectionTestUtils
                    .getField(logService, "logPumpThread");
                return !Objects.requireNonNull(logPumpThread).isAlive();
            });
        }
    }

    private void waitForMessageToBeLogged() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> ((List) Objects
            .requireNonNull(ReflectionTestUtils.getField(logService, "pendingMessages")))
            .isEmpty());
    }
}