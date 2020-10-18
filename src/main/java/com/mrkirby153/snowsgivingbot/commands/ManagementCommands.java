package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.GuildSetting;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.transaction.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManagementCommands {

    private static final String TADA = "\uD83C\uDF89";
    private static final Map<String, GuildSetting<?>> settingMap = new HashMap<>();

    static {
        settingMap.put("emote", Settings.GIVEAWAY_EMOTE);
    }

    private final SettingService settingService;
    private final ShardManager shardManager;

    @Value("${bot.prefix:!}")
    private String prefix;

    @Value("${bot.reaction:" + TADA + "}")
    private String defaultEmote;

    @Command(name = "configure", clearance = 100)
    public void getAllSettings(Context context, CommandContext cmdContext) {
        EmbedBuilder eb = new EmbedBuilder();
        String botName = context.getJDA().getSelfUser().getName();
        eb.setTitle(botName + " Settings");
        eb.setColor(Color.BLUE);
        eb.setDescription("Below is the settings for this server. Use `" + prefix
            + "configure set <option> <value>` to change");

        // Giveaway emote
        ConfiguredGiveawayEmote emote = settingService
            .get(Settings.GIVEAWAY_EMOTE, context.getGuild());
        String emoteStr;
        if (emote != null) {
            emoteStr = emote.getEmote();
            if (emote.isCustom()) {
                Emote e = shardManager.getEmoteById(emoteStr);
                if (e == null) {
                    emoteStr = "_Custom emote not found_";
                } else {
                    emoteStr = e.getAsMention();
                }
            }
        } else {
            emoteStr = defaultEmote;
            if (emoteStr.matches("\\d{17,18}")) {
                Emote e = shardManager.getEmoteById(emoteStr);
                if (e == null) {
                    emoteStr = "_Emote not found_";
                } else {
                    emoteStr = e.getAsMention();
                }
            }
        }
        eb.addField("Emote", emoteStr, true);
        context.getChannel().sendMessage(eb.build()).queue();
    }

    @Command(name = "set", clearance = 100, parent = "configure", arguments = {"<key:string>",
        "<value:string...>"})
    public void setSetting(Context context, CommandContext cmdContext) {
        String key = cmdContext.getNotNull("key");
        String value = cmdContext.getNotNull("value");

        if (key.equalsIgnoreCase("emote")) {
            Pattern pattern = Pattern.compile("\\d{17,18}");
            Matcher m = pattern.matcher(value);
            ConfiguredGiveawayEmote emote;
            boolean custom = false;
            Emote e = null;
            if (m.find()) {
                emote = new ConfiguredGiveawayEmote(true, m.group());
                custom = true;
                e = shardManager.getEmoteById(m.group());
                if (e == null) {
                    throw new CommandException(value
                        + " cannot be set as a custom emote because I do not have access to it");
                }
            } else {
                emote = new ConfiguredGiveawayEmote(false, value);
            }
            settingService.set(Settings.GIVEAWAY_EMOTE, context.getGuild(), emote);
            String str = custom ? e.getAsMention() : value;
            context.getChannel().sendMessage("Emote has been set to " + str).queue();
            // TODO: 10/17/20 This does not affect existing giveaways, it should.
            // TODO: 10/17/20 There should also be some sanity checking to ensure they're actually emotes
        }
    }

    @Command(name = "reset", clearance = 100, parent = "configure", arguments = {"<key:string>"})
    @Transactional
    public void resetSetting(Context context, CommandContext cmdContext) {
        String key = cmdContext.getNotNull("key");
        GuildSetting<?> s = settingMap.get(key.toLowerCase());
        settingService.reset(s, context.getGuild());
        context.getChannel().sendMessage(":ok_hand: Setting has been reset!").queue();
    }
}
