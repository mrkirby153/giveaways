package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.IntegrationTest;
import com.mrkirby153.tgabot.TestUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.internal.requests.EmptyRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@IntegrationTest
class DiscordManagerTest {

    @Autowired
    private DiscordService discordService;

    @Mock
    private Emote emote;
    @Mock
    private TextChannel textChannel1;
    @Mock
    private TextChannel textChannel2;
    @Mock
    private Message message;

    @MockBean
    private JDA jda;

    private Guild mockedGuild1;
    private Guild mockedGuild2;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        mockedGuild1 = TestUtils.makeMockedGuild("Guild 1");
        mockedGuild2 = TestUtils.makeMockedGuild("Guild 2");
        when(jda.getGuilds()).thenReturn(Arrays.asList(mockedGuild1, mockedGuild2));
        when(jda.getGuildById(mockedGuild1.getId())).thenReturn(mockedGuild1);
        when(jda.getGuildById(mockedGuild2.getId())).thenReturn(mockedGuild2);
        when(message.getId()).thenReturn("12345");
        when(textChannel1.getId()).thenReturn("55555");
        when(textChannel2.getId()).thenReturn("66666");
    }

    @Test
    void findEmoteById() {
        when(mockedGuild2.getEmoteById("1234")).thenReturn(emote);
        assertThat(discordService.findEmoteById("1234")).isEqualTo(emote);

        assertThatThrownBy(() -> discordService.findEmoteById("5678")).isInstanceOf(
            NoSuchElementException.class)
            .hasMessageContaining("The emote with the id 5678 was not found");
    }

    @Test
    void findMessage() {
        when(mockedGuild1.getTextChannelById("123456789123456789")).thenReturn(textChannel1);

        when(textChannel1.retrieveMessageById("123456789123456789"))
            .thenReturn(new EmptyRestAction<>(jda, message));
        when(textChannel1.retrieveMessageById("123456789123456780"))
            .thenReturn(new EmptyRestAction<>(jda, null));

        String jumpLink = "https://discordapp.com/channels/" + mockedGuild1.getId()
            + "/123456789123456789/123456789123456789";
        assertThat(discordService.findMessage(jumpLink)).isEqualTo(message);

        assertThatThrownBy(() -> discordService.findMessage("$$$INVALID$$$"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("The provided jump link was not a jump link");
        assertThatThrownBy(
            () -> discordService.findMessage(
                "https://discordapp.com/channels/" + mockedGuild1.getId()
                    + "/123456789123456789/123456789123456780"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findMessageById() {
        when(mockedGuild1.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(textChannel1.retrieveMessageById(message.getId()))
            .thenReturn(new EmptyRestAction<>(jda, message));

        assertThat(discordService.findMessageById(mockedGuild1, message.getId()))
            .isEqualTo(message);
        HashMap<String, String> channelCache = (HashMap<String, String>) ReflectionTestUtils
            .getField(discordService, "messageChannelCache");
        assertThat(channelCache).isNotNull();
        assertThat(channelCache.get(message.getId())).isEqualTo(textChannel1.getId());
    }
}