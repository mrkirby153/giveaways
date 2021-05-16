package com.mrkirby153.snowsgivingbot.services.slashcommands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SlashCommand {

    /**
     * The name of the slash command
     *
     * @return The command name
     */
    String name();

    /**
     * The description of the slash command
     *
     * @return The command description
     */
    String description();

    /**
     * The clearance required to run the slash command
     *
     * @return The clearance required to execute this command
     */
    int clearance() default 0;
}
