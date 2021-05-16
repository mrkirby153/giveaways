package com.mrkirby153.snowsgivingbot.services.slashcommands;

import lombok.Data;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Data
public class SlashCommandNode {

    private final String name;
    private String description = "No description provided";
    private final List<SlashCommandNode> children = new ArrayList<>();
    private SlashCommandNode parent;
    private Object classInstance;
    private Method method;
    private List<OptionData> options;

    /**
     * Gets the slash command node by its child name
     *
     * @param name The name of the child
     *
     * @return The slash command
     */
    public SlashCommandNode getByChildName(String name) {
        for (SlashCommandNode n : this.children) {
            if (n.name.equals(name)) {
                return n;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "SlashCommandNode{" +
            "name='" + name + '\'' +
            ", children=" + children +
            '}';
    }
}
