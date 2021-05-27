package com.mrkirby153.snowsgivingbot.services.setting;

import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.settings.ArraySetting;
import com.mrkirby153.snowsgivingbot.services.setting.settings.BooleanSetting;
import com.mrkirby153.snowsgivingbot.services.setting.settings.LongSetting;
import com.mrkirby153.snowsgivingbot.services.setting.settings.ObjectSetting;
import com.mrkirby153.snowsgivingbot.services.setting.settings.StringSetting;

import java.util.concurrent.TimeUnit;

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
     * If jump links should be displayed in giveaway end messages
     */
    public static final BooleanSetting DISPLAY_JUMP_LINKS = new BooleanSetting("jumplinks", true);
    /**
     * Giveaways older than this day will be hidden from the dashboard
     */
    public static final LongSetting HIDE_GIVEAWAYS_DASHBOARD_AGE = new LongSetting("dashboard-age",
        TimeUnit.MILLISECONDS.convert(3, TimeUnit.DAYS));
    /**
     * Enable the usage of buttons for giveaways
     *
     * <b>ALPHA:</b> This setting requires users to be in the support server to utilize
     */
    public static final BooleanSetting USE_BUTTONS = new BooleanSetting("buttons", false);
    /**
     * A list of alphas that this guild is a member of.
     */
    public static final ArraySetting<String> INCLUDED_ALPHAS = new ArraySetting<>("alphas", String.class);
    /**
     * The command prefix for the bot
     */
    public static StringSetting COMMAND_PREFIX = new StringSetting("prefix", "!");
}
