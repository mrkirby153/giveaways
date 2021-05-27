package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.slashcommand.SlashCommand;
import com.mrkirby153.botcore.command.slashcommand.SlashCommandParameter;
import com.mrkirby153.snowsgivingbot.services.GiveawayService.ConfiguredGiveawayEmote;
import com.mrkirby153.snowsgivingbot.services.setting.GuildSetting;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import me.mrkirby153.kcutils.Time.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ManagementSlashCommands {

    public static final Pattern EMOTE_PATTERN = Pattern.compile("\\d{17,18}");
    private static final String TADA = "\uD83C\uDF89";
    private final List<EditableSetting<?>> editableSettings = new ArrayList<>();
    private final SettingService settingService;
    @Value("${bot.reaction:" + TADA + "}")
    private String defaultEmote;

    public ManagementSlashCommands(SettingService settingService, ShardManager shardManager) {
        this.settingService = settingService;

        editableSettings.add(
            new EditableSetting<>(Settings.COMMAND_PREFIX, "Command Prefix", "prefix",
                Objects::toString, (v) -> v)
        );
        editableSettings.add(
            new EditableSetting<>(Settings.GIVEAWAY_EMOTE, "Giveaway Emote",
                "emote", (obj) -> {
                ConfiguredGiveawayEmote cge = (ConfiguredGiveawayEmote) obj;
                String toDisplay = defaultEmote;
                if (obj != null) {
                    toDisplay = cge.getEmote();
                }
                if (toDisplay.matches("\\d{17,18}")) {
                    Emote jdaEmote = shardManager.getEmoteById(toDisplay);
                    if (jdaEmote == null) {
                        return "__Emote Not Found__";
                    } else {
                        return jdaEmote.getAsMention();
                    }
                } else {
                    return toDisplay;
                }
            }, (value) -> {
                Matcher m = EMOTE_PATTERN.matcher(value);
                ConfiguredGiveawayEmote emote;
                if (m.find()) {
                    emote = new ConfiguredGiveawayEmote(true, m.group());
                    Emote e = shardManager.getEmoteById(m.group());
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
                "jumplinks", Objects::toString, (input) -> {
                String lower = input.toLowerCase();
                if (lower.equals("true") || lower.equals("false")) {
                    return Boolean.parseBoolean(lower);
                } else {
                    throw new IllegalArgumentException("Enter true or false");
                }
            }));
        editableSettings.add(
            new EditableSetting<>(Settings.HIDE_GIVEAWAYS_DASHBOARD_AGE,
                "Hide Giveaways Older Than", "dashboard-age", (obj) -> {
                try {
                    return Time.formatLong((Long) obj, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return "Unknown";
                }
            }, Time::parse)
        );
    }

    @SlashCommand(name = "configure list", clearance = 100, description = "Get a list of all the configuration settings")
    public void getAllSettings(SlashCommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        String botName = event.getJDA().getSelfUser().getName();
        eb.setTitle(String.format("%s Settings", botName));
        eb.setColor(Color.BLUE);
        eb.setDescription(
            "The following settings are configured on this server.\n\nUse /configure set <option> <value> to change");
        editableSettings.forEach(setting -> {
            String name = String.format("%s (%s)", setting.getFriendlyName(), setting.getKey());
            Object o = settingService.get(setting.getSetting(), event.getGuild());
            String display = setting.displayValue.apply(o);
            eb.addField(name, display, true);
        });
        event.replyEmbeds(eb.build()).queue();
    }

    @SlashCommand(name = "configure set", clearance = 100, description = "Sets a setting")
    public void set(SlashCommandEvent event,
        @SlashCommandParameter(name = "key", description = "The key to set") String key,
        @SlashCommandParameter(name = "value", description = "The value to set the key to") String value) {
        EditableSetting<?> setting = this.editableSettings.stream()
            .filter(s -> s.getKey().equalsIgnoreCase(key)).findFirst()
            .orElseThrow(() -> new CommandException("Setting not found"));
        Object val;
        try {
            val = setting.getParse().apply(value);
        } catch (Exception e) {
            throw new CommandException(
                e.getMessage() != null ? e.getMessage() : "An unknown error occurred");
        }
        settingService.set(setting.getSetting(), event.getGuild(), val);
        event.replyFormat(":ok_hand: Updated **%s** successfully", setting.getFriendlyName())
            .allowedMentions(
                Collections.emptyList()).queue();
    }

    @SlashCommand(name = "configure reset", clearance = 100, description = "Resets a setting")
    public void reset(SlashCommandEvent event,
        @SlashCommandParameter(name = "key", description = "The key to reset") String key) {
        EditableSetting<?> setting = this.editableSettings.stream()
            .filter(s -> s.getKey().equalsIgnoreCase(key)).findFirst()
            .orElseThrow(() -> new CommandException("Setting not found"));
        settingService.reset(setting.getSetting(), event.getGuild());
        event.replyFormat(":ok_hand: Reset **%s** successfully", setting.getFriendlyName()).queue();
    }

    @Data
    private static class EditableSetting<T> {

        private final GuildSetting<T> setting;
        private final String friendlyName;
        private final String key;
        private final Function<Object, String> displayValue;
        private final Function<String, T> parse;
    }
}
