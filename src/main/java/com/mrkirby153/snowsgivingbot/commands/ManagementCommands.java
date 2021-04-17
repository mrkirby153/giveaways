package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.GuildSetting;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import me.mrkirby153.kcutils.Time.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ManagementCommands {

    private static final String TADA = "\uD83C\uDF89";
    private static final Map<String, GuildSetting<?>> settingMap = new HashMap<>();

    static {
        settingMap.put("emote", Settings.GIVEAWAY_EMOTE);
        settingMap.put("prefix", Settings.COMMAND_PREFIX);
        settingMap.put("jumplinks", Settings.DISPLAY_JUMP_LINKS);
        settingMap.put("dashboard-age", Settings.HIDE_GIVEAWAYS_DASHBOARD_AGE);
    }

    private final List<EditableSetting<?>> editableSettings = new ArrayList<>();
    private final SettingService settingService;
    private final ShardManager shardManager;

    @Value("${bot.reaction:" + TADA + "}")
    private String defaultEmote;

    public ManagementCommands(SettingService settingService, ShardManager shardManager) {
        this.settingService = settingService;
        this.shardManager = shardManager;

        editableSettings.add(
            new EditableSetting<>(Settings.COMMAND_PREFIX, "Command Prefix",
                (display) -> (String) display,
                (val) -> val));
        editableSettings
            .add(new EditableSetting<>(Settings.GIVEAWAY_EMOTE, "Giveaway Emote", (obj) -> {
                ConfiguredGiveawayEmote display = (ConfiguredGiveawayEmote) obj;
                String emoteStr = defaultEmote;
                if (display != null) {
                    emoteStr = display.getEmote();
                }
                if (emoteStr.matches("\\d{17,18}")) {
                    Emote jdaEmote = shardManager.getEmoteById(emoteStr);
                    if (jdaEmote == null) {
                        return "_Emote not found_";
                    } else {
                        return jdaEmote.getAsMention();
                    }
                } else {
                    return emoteStr;
                }
            }, (value) -> {
                Pattern pattern = Pattern.compile("\\d{17,18}");
                Matcher m = pattern.matcher(value);
                ConfiguredGiveawayEmote emote;
                Emote e;
                if (m.find()) {
                    emote = new ConfiguredGiveawayEmote(true, m.group());
                    e = shardManager.getEmoteById(m.group());
                    if (e == null) {
                        throw new IllegalArgumentException(value
                            + " cannot be set as a giveaway emote because I do not have access to it");
                    }
                } else {
                    emote = new ConfiguredGiveawayEmote(false, value);
                }
                return emote;
            }));
        editableSettings.add(
            new EditableSetting<>(Settings.DISPLAY_JUMP_LINKS, "Display Jump Links",
                Object::toString, (input) -> {
                String lower = input.toLowerCase();
                if(lower.equals("true") || lower.equals("false")) {
                    return Boolean.parseBoolean(lower);
                } else {
                    throw new IllegalArgumentException("Enter true or false");
                }
            }));
        editableSettings.add(new EditableSetting<>(Settings.HIDE_GIVEAWAYS_DASHBOARD_AGE,
            "Hide Giveaways Older Than", obj -> {
            try {
                return Time.formatLong((long) obj, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "Unknown";
            }
        }, Time::parse));
    }

    @Command(name = "configure", clearance = 100, permissions = {Permission.MESSAGE_EMBED_LINKS})
    public void getAllSettings(Context context, CommandContext cmdContext) {
        EmbedBuilder eb = new EmbedBuilder();
        String botName = context.getJDA().getSelfUser().getName();
        eb.setTitle(botName + " Settings");
        eb.setColor(Color.BLUE);
        String prefix = settingService
            .get(Settings.COMMAND_PREFIX, context.getGuild());
        eb.setDescription("The following settings are configured on this server.\n\nUse `" + prefix
            + "configure set <option> <value>` to change (i.e. `" + prefix
            + "configure set emote :package:`");

        editableSettings.forEach(setting -> {
            String name = String
                .format("%s (%s)", setting.getFriendlyName(), setting.getGuildSetting().getKey());
            Object o = settingService.get(setting.getGuildSetting(), context.getGuild());
            String display = setting.displayName.apply(o);
            eb.addField(name, display, true);
        });
        context.getChannel().sendMessage(eb.build()).queue();
    }

    @Command(name = "set", clearance = 100, parent = "configure", arguments = {"<key:string>",
        "<value:string...>"})
    public void setSetting(Context context, CommandContext cmdContext) {
        String key = cmdContext.getNotNull("key");
        String value = cmdContext.getNotNull("value");

        Optional<EditableSetting<?>> settingOpt = editableSettings.stream()
            .filter(s -> s.getGuildSetting().getKey().equalsIgnoreCase(key)).findFirst();
        if (settingOpt.isEmpty()) {
            throw new CommandException("Setting not found");
        }
        EditableSetting<?> setting = settingOpt.get();
        Object val;
        try {
            val = setting.getParser().apply(value);
        } catch (Exception e) {
            throw new CommandException(
                e.getMessage() != null ? e.getMessage() : "An unknown error occurred");
        }
        settingService.set(setting.getGuildSetting(), context.getGuild(), val);
        context.getChannel().sendMessage(":ok_hand: Setting has been updated").queue();
    }

    @Command(name = "reset", clearance = 100, parent = "configure", arguments = {"<key:string>"})
    public void resetSetting(Context context, CommandContext cmdContext) {
        String key = cmdContext.getNotNull("key");
        GuildSetting<?> s = settingMap.get(key.toLowerCase());
        settingService.reset(s, context.getGuild());
        context.getChannel().sendMessage(":ok_hand: Setting has been reset!").queue();
    }

    @Data
    @AllArgsConstructor
    private static class EditableSetting<T> {

        private final GuildSetting<?> guildSetting;
        private final String friendlyName;
        private final Function<Object, String> displayName;
        private final Function<String, T> parser;
    }
}
