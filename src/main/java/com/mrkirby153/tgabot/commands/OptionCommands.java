package com.mrkirby153.tgabot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.OptionRepository;
import com.mrkirby153.tgabot.services.DiscordService;
import com.mrkirby153.tgabot.services.PollMessageService;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Emote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
@Transactional
public class OptionCommands {

    private static final Pattern customEmotePattern = Pattern.compile("<a?:.*:([0-9]{17,18})");

    private OptionRepository optionRepository;
    private CategoryRepository categoryRepository;
    private DiscordService discordService;
    private PollMessageService pollMessageService;

    @Command(parent = "option", clearance = 100, name = "add", arguments = {"<category:number>",
        "<emoji:string>", "<name:string...>"})
    public void addOption(Context context, CommandContext cmdContext) {
        Optional<Category> cat = categoryRepository
            .findById(cmdContext.<Double>getNotNull("category").longValue());
        if (!cat.isPresent()) {
            throw new CommandException("That category does not exist");
        }

        Matcher matcher = customEmotePattern.matcher(cmdContext.getNotNull("emoji"));
        String emote = "";
        boolean custom = false;
        if (matcher.find()) {
            // Found a custom emote
            emote = matcher.group(1);
            custom = true;
        } else {
            // We didn't find a custom emote
            emote = cmdContext.getNotNull("emoji");
        }

        Emote customEmote = null;
        if (custom) {
            // Verify that the emote actually exists and we can use it
            try {
                customEmote = discordService.findEmoteById(emote);
            } catch (NoSuchElementException e) {
                throw new CommandException(
                    "The provided emote was not in a guild that I have access to, so you can't use it.");
            }
        }
        Option option = new Option(cat.get(), custom, emote, cmdContext.getNotNull("name"));
        option = optionRepository.saveAndFlush(option);

        String emoteMention = customEmote != null ? customEmote.getAsMention() : emote;
        context.getChannel().sendMessage(
            ":ok_hand: Added " + emoteMention + " as an option for **" + cat.get().getName()
                + "** (" + option.getId() + ")").queue();
        pollMessageService.updatePollMessage(cat.get());
        pollMessageService.updateReactions(cat.get());
    }

    @Command(parent = "option", clearance = 100, name = "remove", arguments = {"<id:int>"})
    public void removeOption(Context context, CommandContext cmdContext) {
        Long id = cmdContext.<Integer>getNotNull("id").longValue();
        Option option = getOptionalOrError(optionRepository.findById(id),
            "That option was not found");
        Category category = option.getCategory();

        optionRepository.deleteById(id);
        optionRepository.flush();
        String emoteMention =
            option.isCustom() ? discordService.findEmoteById(option.getReaction()).getAsMention()
                : option.getReaction();
        context.getChannel()
            .sendMessage(
                ":ok_hand: Removed " + emoteMention + " from **" + category.getName() + "**")
            .queue();
        pollMessageService.updatePollMessage(category);
        pollMessageService.updateReactions(category);
    }

    @Command(name = "options", clearance = 100, arguments = {"<id:int>"})
    public void getOptions(Context context, CommandContext cmdContext) {
        Long id = cmdContext.<Integer>getNotNull("id").longValue();
        Category category = getOptionalOrError(categoryRepository.findById(id),
            "That category was not found");
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(category.getName()).append("** has the following options: \n\n");
        category.getOptions().forEach(option -> {
            String mention = option.isCustom() ? discordService.findEmoteById(option.getReaction())
                .getAsMention() : option.getReaction();
            sb.append(
                String.format(" - %d. %s **%s**\n", option.getId(), mention, option.getName()));
        });

        context.getChannel().sendMessage(sb.toString()).queue();
    }

    @Command(parent = "option", clearance = 100, name = "rename", arguments = {"<id:int>",
        "<name:string...>"})
    public void renameOption(Context context, CommandContext cmdContext) {
        Long id = cmdContext.<Integer>getNotNull("id").longValue();
        Option option = getOptionalOrError(optionRepository.findById(id),
            "That option was not found");

        option.setName(cmdContext.getNotNull("name"));
        optionRepository.save(option);
        context.getChannel().sendMessage(
            ":ok_hand: Renamed option for **" + option.getCategory().getName() + "** to `" + option
                .getName() + "`").queue();
        pollMessageService.updatePollMessage(option.getCategory());
    }

    @Command(parent = "option", clearance = 100, name = "emote", arguments = {"<id:int>",
        "<emoji:string>"})
    public void changeEmote(Context context, CommandContext cmdContext) {
        Option option = optionRepository.findById(cmdContext.<Integer>getNotNull("id").longValue())
            .orElseThrow(() -> new CommandException("Option not found"));
        Matcher matcher = customEmotePattern.matcher(cmdContext.getNotNull("emoji"));
        String emote;
        boolean custom = false;
        if (matcher.find()) {
            // Found a custom emote
            emote = matcher.group(1);
            custom = true;
        } else {
            // We didn't find a custom emote
            emote = cmdContext.getNotNull("emoji");
        }

        Emote customEmote = null;
        if (custom) {
            // Verify that the emote actually exists and we can use it
            try {
                customEmote = discordService.findEmoteById(emote);
            } catch (NoSuchElementException e) {
                throw new CommandException(
                    "The provided emote was not in a guild that I have access to, so you can't use it.");
            }
        }

        option.setCustom(custom);
        option.setReaction(emote);
        optionRepository.saveAndFlush(option);

        String emoteMention =
            option.isCustom() ? discordService.findEmoteById(option.getReaction()).getAsMention()
                : option.getReaction();
        context.getChannel()
            .sendMessage("Updated the emote for **" + option.getName() + "** to " + emoteMention)
            .queue();
        pollMessageService.updatePollMessage(option.getCategory());
        pollMessageService.updateReactions(option.getCategory());
    }


    private <T> T getOptionalOrError(Optional<T> optional, String error) {
        if (!optional.isPresent()) {
            throw new CommandException(error);
        }
        return optional.get();
    }
}
