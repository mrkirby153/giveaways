package com.mrkirby153.snowsgivingbot.services.setting;

import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.settings.ObjectSetting;
import com.mrkirby153.snowsgivingbot.services.setting.settings.StringSetting;

/**
 * A list of all settings available on the bot
 */
public class Settings {

    /**
     * The custom emote available for the bot
     */
    public static final ObjectSetting<ConfiguredGiveawayEmote> GIVEAWAY_EMOTE = new ObjectSetting<>(
        "emote", ConfiguredGiveawayEmote.class, null);

    /**
     * The command prefix for the bot
     */
    public static StringSetting COMMAND_PREFIX = new StringSetting("prefix", "!");
}
