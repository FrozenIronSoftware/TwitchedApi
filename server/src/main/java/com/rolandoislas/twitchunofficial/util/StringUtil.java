package com.rolandoislas.twitchunofficial.util;

import org.jetbrains.annotations.Contract;
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

    /**
     * Safely parse a string to a boolean value
     * A value of case-insensitive "true" will result in a true boolean.
     * A "0" will be considered true and a "1" will be considered false.
     * Anything else will be false. This includes null and case-insensitive "false".
     * @param bool string to parse
     * @return string to boolean
     */
    @Contract("null -> false")
    public static boolean parseBoolean(@Nullable String bool) {
        if (bool == null)
            return false;
        if (bool.equals("0"))
            return false;
        if (bool.equals("1"))
            return true;
        return Boolean.parseBoolean(bool);
    }
}
