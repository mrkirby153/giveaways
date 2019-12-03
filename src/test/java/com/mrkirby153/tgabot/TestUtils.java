package com.mrkirby153.tgabot;

import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.utils.SnowflakeWorker;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class TestUtils {

    private static SnowflakeWorker snowflakeWorker = new SnowflakeWorker(1, 1, 0);
    public static final User TEST_USER1 = makeMockUser("Test User 1", "0001");
    public static final User TEST_USER2 = makeMockUser("Test User 2", "0002");
    public static final Guild GUILD = makeMockedGuild("Test Guild");

    /**
     * Makes a mocked user
     *
     * @param username      The username
     * @param discriminator The discriminator
     *
     * @return The user
     */
    public static User makeMockUser(String username, String discriminator) {
        User mockedUser = mock(User.class);
        when(mockedUser.getId()).thenReturn(Long.toString(snowflakeWorker.generate()));
        when(mockedUser.getName()).thenReturn(username);
        when(mockedUser.getDiscriminator()).thenReturn(discriminator);
        when(mockedUser.isBot()).thenReturn(false);
        return mockedUser;
    }

    /**
     * Makes a mocked guild
     *
     * @param guildName The name of the guild
     *
     * @return The guild
     */
    public static Guild makeMockedGuild(String guildName) {
        Guild mockedGuild = mock(Guild.class);
        when(mockedGuild.getId()).thenReturn(Long.toString(snowflakeWorker.generate()));
        when(mockedGuild.getName()).thenReturn(guildName);
        return mockedGuild;
    }

    public static TextChannel makeMockedTextChannel(Guild guild, String name) {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getGuild()).thenReturn(guild);
        when(channel.getId()).thenReturn(Long.toOctalString(snowflakeWorker.generate()));
        when(channel.getName()).thenReturn(name.replace(' ', '-'));
        when(guild.getTextChannelById(channel.getId())).thenReturn(channel);
        return channel;
    }
}
