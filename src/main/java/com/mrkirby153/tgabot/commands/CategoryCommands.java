package com.mrkirby153.tgabot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.tgabot.config.BotConfig;
import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.repo.CategoryRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import com.mrkirby153.tgabot.services.DiscordService;
import com.mrkirby153.tgabot.services.PollMessageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@AllArgsConstructor
@Transactional
public class CategoryCommands {

    private final CategoryRepository categoryRepo;
    private final VoteRepository voteRepo;
    private final EventWaiter eventWaiter;
    private final PollMessageService pollMessageService;
    private final DiscordService discordService;

    @Command(name = "create", clearance = 100, parent = "category", arguments = {"<name:string>",
        "[channel:idormention]"})
    public void createCategory(Context context, CommandContext cmdContext) {
        String name = cmdContext.get("name");
        String channelId = cmdContext.get("channel");
        if (channelId == null) {
            channelId = context.getChannel().getId();
        }

        TextChannel channel = context.getGuild().getTextChannelById(channelId);

        if (channel == null) {
            throw new CommandException("The channel with id `" + channelId + "` does not exist!");
        }

        if (categoryRepo.findByNameIgnoreCaseAndGuild(name, context.getGuild().getId()) != null) {
            throw new CommandException("A category with that name already exists on this guild");
        }

        Category category = new Category(name, context.getGuild().getId(), channelId);
        category = categoryRepo.save(category);
        pollMessageService.updatePollMessage(category);
        context.getChannel()
            .sendMessage(
                ":ok_hand: Created category `" + name + "` (" + category.getId() + ") in " + channel
                    .getAsMention()).queue();
        log.info("Created category {} ({})", name, category.getId());
    }

    @Command(name = "categories", clearance = 100)
    public void listCategories(Context context, CommandContext cmdContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("The following categories currently exist:\n\n```\n");
        if (categoryRepo.count() == 0) {
            sb.append("No categories currently exist");
        } else {
            categoryRepo.findAll().forEach(category -> {
                String toAdd = String
                    .format("- %d. %s (%d options)\n", category.getId(), category.getName(),
                        category.getOptions().size());
                if (sb.length() + toAdd.length() > 1990) {
                    sb.append("```");
                    context.getChannel().sendMessage(sb.toString()).queue();
                    sb.setLength(0);
                    sb.append("```");
                } else {
                    sb.append(toAdd);
                }
            });
        }
        sb.append("```");
        context.getChannel().sendMessage(sb.toString()).queue();
    }

    @Command(name = "remove", parent = "category", clearance = 100, arguments = {"<id:int>"})
    public void removeCategory(Context context, CommandContext cmdContext) {
        Optional<Category> categoryOpt = categoryRepo
            .findById(cmdContext.<Integer>getNotNull("id").longValue());
        if (!categoryOpt.isPresent()) {
            throw new CommandException("That category does not exist");
        }
        final Category category = categoryOpt.get();
        if (category.getOptions().stream().anyMatch(o -> voteRepo.countAllByOption(o) > 0)) {
            context.getChannel().sendMessage(
                ":warning: This category has options with responses. Are you sure you want to delete it?")
                .queue(message -> {
                    message.addReaction(BotConfig.GREEN_CHECK).queue(v -> {
                        message.addReaction(BotConfig.RED_CROSS).queue();
                    });
                    eventWaiter.waitFor(MessageReactionAddEvent.class, event ->
                            event.getMessageId().equals(message.getId()) && event.getUser().getId()
                                .equals(context.getAuthor().getId()) && (
                                event.getReactionEmote().getName().equals(BotConfig.GREEN_CHECK)
                                    || event.getReactionEmote().getName().equals(BotConfig.RED_CROSS)),
                        event -> {
                            if (event.getReactionEmote().getName().equals(BotConfig.RED_CROSS)) {
                                message.editMessage("Canceled").queue();
                            } else {
                                message.editMessage("Deleted").queue();
                                categoryRepo.delete(category);
                                pollMessageService.removeCategory(category);
                                log.info("Removed category {} ({})", category.getName(),
                                    category.getId());
                            }
                        }, 10, TimeUnit.SECONDS);
                });
        } else {
            categoryRepo.delete(category);
            pollMessageService.removeCategory(category);
            log.info("Removed category {} ({})", category.getName(), category.getId());
            context.getChannel()
                .sendMessage(":ok_hand: Deleted the category `" + category.getName() + "`")
                .queue();
        }
    }

    @Command(name = "rename", parent = "category", clearance = 100, arguments = {"<id:int>",
        "<name:string...>"})
    public void renameCategory(Context context, CommandContext cmdContext) {
        String name = cmdContext.getNotNull("name");
        Integer id = cmdContext.getNotNull("id");

        Optional<Category> categoryOpt = categoryRepo.findById(id.longValue());
        if (!categoryOpt.isPresent()) {
            throw new CommandException("That category does not exist");
        }

        Category category = categoryOpt.get();
        String prevName = category.getName();
        category.setName(name);
        category = categoryRepo.save(category);
        pollMessageService.updatePollMessage(category);
        log.info("Renamed category {} ({}) to {}", prevName, category.getId(), name);
        context.getChannel()
            .sendMessage(":ok_hand: Renamed category from `" + prevName + "` → `" + name + "`")
            .queue();
    }

    @Command(name = "refresh", parent = "category", clearance = 100, arguments = {"<id:int>"})
    public void refreshCategory(Context context, CommandContext cmdContext) {
        Integer id = cmdContext.getNotNull("id");
        Optional<Category> categoryOpt = categoryRepo.findById(id.longValue());
        if (!categoryOpt.isPresent()) {
            throw new CommandException("That category does not exist");
        }
        Category category = categoryOpt.get();
        pollMessageService.updatePollMessage(category);
        pollMessageService.updateReactions(category);
        context.getChannel().sendMessage(
            ":ok_hand: Updated the message for **" + category.getName() + "**").queue();
    }

    @Command(name = "import", parent = "category", clearance = 100)
    public void importCategories(Context context, CommandContext commandContext)
        throws IOException {
        if (context.getAttachments().size() < 1) {
            throw new CommandException("Please attach a json file of categories to import");
        }

        String attachmentUrl = context.getAttachments().get(0).getUrl();
        URL url = new URL(attachmentUrl);
        URLConnection conn = url.openConnection();
        JSONArray json = new JSONArray(new JSONTokener(conn.getInputStream()));
        context.getChannel().sendMessage("Importing...").queue();

        List<Category> categories = new ArrayList<>();
        for (Object rawObj : json) {
            if (!(rawObj instanceof JSONObject)) {
                throw new CommandException("Invalid json schema provided");
            }
            JSONObject category = (JSONObject) rawObj;
            String channelId = category.getString("channel");
            String name = category.getString("name");

            JSONArray options = category.getJSONArray("options");
            Category categoryEntity = new Category(name, context.getGuild().getId(), channelId);

            int num = 1;
            for (Object rawOption : options) {
                if (!(rawOption instanceof JSONObject)) {
                    throw new CommandException("Invalid json schema provided");
                }
                JSONObject option = (JSONObject) rawOption;
                boolean custom = option.getBoolean("custom");
                String emote = option.getString("emote");
                String optionName = option.getString("name");

                if (custom) {
                    try {
                        discordService.findEmoteById(emote);
                    } catch (NoSuchElementException e) {
                        throw new CommandException(
                            "Could not find an emote with the id `" + emote + "`. Category: **"
                                + categoryEntity.getName() + "** Option: **" + optionName + "**");
                    }
                }

                if (emote.isEmpty()) {
                    emote = num++ + "\uFE0F\u20E3";
                }
                categoryEntity.getOptions()
                    .add(new Option(categoryEntity, custom, emote, optionName));
            }
            categories.add(categoryEntity);

        }
        categoryRepo.saveAll(categories);
        categories.forEach(pollMessageService::updatePollMessage);
        context.getChannel().sendMessage(":ok_hand: Imported categories.").queue();
    }

    @Command(name = "tally", parent = "category", clearance = 100, arguments = {"<category:int>"})
    public void tally(Context context, CommandContext cmdContext) {
        Integer categoryId = cmdContext.getNotNull("category");

        Category cat = categoryRepo.findById(categoryId.longValue())
            .orElseThrow(() -> new CommandException("Category not found"));
        context.getChannel().sendMessage("Tallying...").queue(m -> {
            m.editMessage(buildResultMessage(cat)).queue();
        });
    }

    @Command(name = "tally-all", parent = "category", clearance = 100)
    public void tallyAll(Context context, CommandContext commandContext) {
        context.getChannel().sendMessage("Tallying...").queue(m -> {
            StringBuilder results = new StringBuilder();
            categoryRepo.findAll().forEach(category -> results.append(buildResultMessage(category))
                .append("\n\n===============\n\n"));
            context.getChannel().sendFile(results.toString().getBytes(), "results.txt").queue();
            m.editMessage("Done!").queue();
        });
    }

    @NotNull
    private String buildResultMessage(Category cat) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Results for ").append(cat.getName()).append("**\n\n");

        Option highestOption = null;
        long highestCount = 0;
        for (Map.Entry<Option, Long> option : tally(cat).entrySet()) {
            sb.append(" - ").append(option.getKey().getName()).append(": **")
                .append(option.getValue()).append(" votes**\n");
            if (option.getValue() > highestCount) {
                highestCount = option.getValue();
                highestOption = option.getKey();
            }
        }
        sb.append("\n\nWinner: ").append(highestOption != null ? highestOption.getName() : "Error")
            .append(" with **").append(highestCount).append(" votes**");
        return sb.toString();
    }

    private Map<Option, Long> tally(Category category) {
        Map<Option, Long> results = new HashMap<>();
        categoryRepo.tally(category)
            .forEach(t -> results.put(t.get(0, Option.class), t.get(1, Long.class)));
        return results;
    }

}
