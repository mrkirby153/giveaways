package com.mrkirby153.snowsgivingbot.services.slashcommands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to bind a slash command option to a parameter. The type is automatically inferred.
 * If the {@link javax.annotation.Nullable} annotation is provided, this parameter will be optional
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CommandOption {

    /**
     * The name of the option
     *
     * @return The name of the option
     */
    String value();

    String description() default "No description provided";
}
