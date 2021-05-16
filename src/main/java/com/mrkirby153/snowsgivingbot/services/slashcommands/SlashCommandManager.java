package com.mrkirby153.snowsgivingbot.services.slashcommands;

import com.mrkirby153.snowsgivingbot.commands.slashcommands.TestSlashCommands;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.CommandOption;
import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.SlashCommand;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

@Service
@Slf4j
public class SlashCommandManager implements SlashCommandService {

    private static final Map<Class<?>, OptionType> optionTypes = new HashMap<>();
    private static final List<Class<?>> slashCommandClasses = new ArrayList<>();

    static {
        optionTypes.put(Boolean.class, OptionType.BOOLEAN);
        optionTypes.put(Integer.class, OptionType.INTEGER);
        optionTypes.put(String.class, OptionType.STRING);

        slashCommandClasses.add(TestSlashCommands.class);
    }

    private final ShardManager shardManager;
    private final SlashCommandNode rootNode = new SlashCommandNode("$$ROOT$$");
    private final ApplicationContext context;

    private final String slashCommandGuilds;

    public SlashCommandManager(ShardManager shardManager, ApplicationContext applicationContext,
        @Value("${bot.slash-command.guilds:}") String slashCommandGuilds) {
        this.shardManager = shardManager;
        this.slashCommandGuilds = slashCommandGuilds;
        this.context = applicationContext;
    }


    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        slashCommandClasses.forEach(this::registerSlashCommands);
        this.commitSlashCommands();
    }

    @Override
    public void registerSlashCommands(Class<?> clazz) {
        try {
            registerSlashCommands(context.getBean(clazz));
            log.info("Registered slash commands for bean {}", clazz);
        } catch (NoSuchBeanDefinitionException ex) {
            try {
                registerSlashCommands(clazz.getConstructor().newInstance());
                log.info("Registered slash commands for class {}", clazz);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerSlashCommands(Object object) {
        Arrays.stream(object.getClass().getDeclaredMethods()).filter(m -> m.isAnnotationPresent(
            SlashCommand.class)).forEach(method -> {
            SlashCommand annotation = method.getAnnotation(SlashCommand.class);
            SlashCommandNode node = resolveNode(
                annotation.name()); // This should always be a leaf node
            node.setOptions(discoverOptions(method));
            node.setClassInstance(object);
            node.setDescription(annotation.description());
        });
    }

    @Override
    public void commitSlashCommands() {
        log.info("Committing discovered slash commands");
        List<CommandData> commands = new ArrayList<>();
        for (SlashCommandNode node : rootNode.getChildren()) {
            CommandData commandData = new CommandData(node.getName(), node.getDescription());
            List<SlashCommandNode> children = node.getChildren();
            boolean hasGroupChildren = false;
            boolean hasSubCommand = false; // These two options are mutually exclusive
            for (SlashCommandNode child : children) {
                if (child.getChildren().size() > 0) {
                    if (hasSubCommand) {
                        throw new IllegalArgumentException("Cannot commit");
                    }
                    hasGroupChildren = true;
                } else {
                    if (hasGroupChildren) {
                        throw new IllegalArgumentException("Cannot commit");
                    }
                    hasSubCommand = true;
                }
            }

            if (hasSubCommand) {
                for (SlashCommandNode child : children) {
                    SubcommandData subcommandData = new SubcommandData(child.getName(),
                        child.getDescription());
                    child.getOptions().forEach(subcommandData::addOption);
                    commandData.addSubcommand(subcommandData);
                }
            } else if (hasGroupChildren) {
                for (SlashCommandNode child : children) {
                    List<SlashCommandNode> subChildren = child.getChildren();
                    SubcommandGroupData subcommandGroupData = new SubcommandGroupData(
                        child.getName(), child.getDescription());
                    for (SlashCommandNode subChild : subChildren) {
                        SubcommandData subcommandData = new SubcommandData(subChild.getName(),
                            subChild.getDescription());
                        subChild.getOptions().forEach(subcommandData::addOption);
                        subcommandGroupData.addSubcommand(subcommandData);
                    }
                    commandData.addSubcommandGroup(subcommandGroupData);
                }
            } else {
                node.getOptions().forEach(commandData::addOption);
            }
            commands.add(commandData);
        }
        if (this.slashCommandGuilds.isBlank()) {
            log.info("Updating commands globally");
        } else {
            for (String guildId : slashCommandGuilds.split(",")) {
                Guild g = shardManager.getGuildById(guildId);
                if (g != null) {
                    log.info("Updating commands in {}", g);
                    g.updateCommands().addCommands(commands).queue();
                }
            }
        }
    }

    @EventListener
    public void executeSlashCommand(SlashCommandEvent event) {
        // Only accept slash commands in servers
        if (!event.isFromGuild()) {
            event.reply("Slash commands currently only work in guilds").setEphemeral(true).queue();
            return;
        }
        event.reply("Testy mctestface " + event.getName() + ":" + event.getSubcommandName())
            .queue();
    }

    /**
     * Resolves a node based on its path (Space delimited)
     *
     * @param path The path to resolve
     *
     * @return The node
     */
    private SlashCommandNode resolveNode(String path) {
        String[] parts = path.split(" ");
        if (parts.length > 3) {
            throw new IllegalArgumentException("Cannot register sub-sub commands");
        }
        SlashCommandNode curr = this.rootNode;
        for (String p : parts) {
            SlashCommandNode potential = curr.getByChildName(p);
            if (potential == null) {
                // There's no child node with this name
                SlashCommandNode newChild = new SlashCommandNode(p);
                curr.getChildren().add(newChild);
                newChild.setParent(curr);
                curr = newChild;
            } else {
                curr = potential;
            }
        }
        return curr;
    }

    private List<OptionData> discoverOptions(Method m) {
        m.trySetAccessible();
        List<OptionData> options = new ArrayList<>();
        Arrays.stream(m.getParameters()).filter(p -> p.isAnnotationPresent(CommandOption.class))
            .forEach(param -> {
                OptionType type = optionTypes.get(param.getType());
                if (type == null) {
                    throw new IllegalArgumentException(
                        "Unrecognized option type for " + param.getType());
                }
                CommandOption annotation = param.getAnnotation(CommandOption.class);
                OptionData data = new OptionData(type, annotation.value(),
                    annotation.description());
                data.setRequired(param.isAnnotationPresent(Nonnull.class));
                options.add(data);
            });
        return options;
    }
}
