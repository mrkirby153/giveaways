package com.mrkirby153.snowsgivingbot.utils;

import java.util.concurrent.CompletionException;

public class ExceptionUtils {

    /**
     * Unwraps a completion exception and gets the actual exception
     *
     * @param throwable The throwable to unwrap
     *
     * @return The unwarpped throwable
     */
    public static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            return throwable.getCause();
        } else {
            return throwable;
        }
    }
}
