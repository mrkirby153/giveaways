package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
public class HelpCommand {

    public static final String DEFAULT_PERMISSIONS = "379968";
    public static final String DISCORD_OAUTH_INVITE = "https://discord.com/api/oauth2/authorize?client_id=%s&permissions=%s&scope=bot%20applications.commands";

    private final SettingService settingService;
    private final String dashUrlFormat;
    private final String permissions;
    private final String supportInvite;

    public HelpCommand(@Value("${bot.dash-url-format}") String dashUrlFormat,
        @Value("${bot.permissions:" + DEFAULT_PERMISSIONS + "}") String permissions,
        @Value("${bot.support-server:}") String supportInvite, SettingService settingService) {
        this.dashUrlFormat = dashUrlFormat;
        this.permissions = permissions;
        this.supportInvite = supportInvite;
        this.settingService = settingService;
    }

    @Command(name = "help", clearance = 100, permissions = {Permission.MESSAGE_EMBED_LINKS})
    public void help(Context context, CommandContext commandContext) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(String.format("%s Help", context.getJDA().getSelfUser().getName()));
        StringBuilder desc = new StringBuilder();
        String prefix = settingService.get(Settings.COMMAND_PREFIX, context.getGuild());
        desc.append("**Commands**\n");
        desc.append(prefix).append(
            "start <duration> [winners] <prize> - Starts a giveaway in the current channel. (Example: `")
            .append(prefix).append("start 30m 2w Cool steam key").append("`)\n");
        desc.append(prefix).append("end <message id> - Ends the giveaway with the message id\n");
        desc.append(prefix)
            .append("winners <message id> - Displays the winners of the provided giveaway\n");
        desc.append(prefix)
            .append(
                "winners set <message id> <winner count> - Sets the amount of winners for a giveaway\n");
        desc.append(prefix)
            .append("reroll <message id> - Picks new winners for the provided giveaway\n");
        desc.append(prefix)
            .append("export - Exports a CSV of all the giveaways ran in this server\n");
        desc.append("\n");
        desc.append(
            "**Giveaway Hosts**\nUsers with **Manage Server** or a giveaway host role can start giveaways.\n\n");
        desc.append(prefix)
            .append("role add <role id> - Adds the provided role as a giveaway host\n");
        desc.append(prefix)
            .append("role remove <role id> - Removes the provided role as a giveaway host\n");
        desc.append(prefix).append("role list - Lists roles configured as a giveaway host\n");
        desc.append(prefix).append("help - Displays this help message\n");
        desc.append(prefix).append("invite - Gets an invite for the bot\n");
        desc.append("\n");
        desc.append("`[ ]` indicates optional arguments, `< >` indicates required arguments\n\n");
        desc.append("Click [here](")
            .append(String.format(dashUrlFormat, context.getGuild().getId()))
            .append(") to view the giveaway dashboard.\n");

        if (!this.supportInvite.isBlank()) {
            desc.append("Join the [support server](").append(this.supportInvite)
                .append(") for additional help\n");
        }
        builder.setDescription(desc);
        builder.setColor(Color.BLUE);
        context.getChannel().sendMessage(builder.build()).queue();
    }

    @Command(name = "invite", permissions = {Permission.MESSAGE_EMBED_LINKS})
    public void invite(Context context, CommandContext commandContext) {
        String prefix = settingService.get(Settings.COMMAND_PREFIX, context.getGuild());
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(String.format("%s Invite", context.getJDA().getSelfUser().getName()));
        builder.setDescription("You can invite me to your server by clicking [here](" + String
            .format(DISCORD_OAUTH_INVITE, context.getJDA().getSelfUser().getId(), permissions)
            + "). \n\nBy default only users with **Manage Server** can start giveaways, but this can be changed. See **"
            + prefix + "help** for more information");
        builder.setColor(Color.BLUE);
        context.getChannel().sendMessage(builder.build()).queue();
    }
}
