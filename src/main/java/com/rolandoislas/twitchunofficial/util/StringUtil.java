package com.rolandoislas.twitchunofficial.util;

import org.jetbrains.annotations.Nullable;

public class StringUtil {
    /**
     * Safely parse an long
     * @param number string to parse
     * @return parsed long or 0 on failure
     */
    public static long parseLong(@Nullable String number) {
        long parsedLong = 0;
        try {
            if (number != null)
                parsedLong = Long.parseLong(number);
        }
        catch (NumberFormatException ignore) {}
        return parsedLong;
    }
}
