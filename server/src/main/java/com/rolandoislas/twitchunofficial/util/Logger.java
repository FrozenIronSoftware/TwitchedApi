/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Created by rolando on 3/21/17.
 */
public class Logger {
    private static final java.util.logging.Logger logger;
    private static final ConsoleHandler consoleHandler;

    static {
        Level level = Level.INFO;
        logger = java.util.logging.Logger.getLogger("TWITCH");
        logger.setLevel(level);
        logger.setUseParentHandlers(false);
        consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new LogFormatter());
        logger.addHandler(consoleHandler);
    }

    public static void warn(String message, Object... o) {
        logger.log(Level.WARNING, message, o);
    }

    public static void debug(String message, Object... o) {
        logger.log(Level.FINE, message, o);
    }

    public static void info(String message, Object... o) {
        logger.log(Level.INFO, message, o);
    }

    public static void exception(Exception e) {
        if (logger.isLoggable(Level.FINER))
            e.printStackTrace();
    }

    public static void extra(String message, Object... o) {
        logger.log(Level.FINER, message, o);
    }

    public static void verbose(String message, Object... o) {
        logger.log(Level.FINEST, message, o);
    }

    public static void setLevel(Level level) {
        Logger.logger.setLevel(level);
        Logger.consoleHandler.setLevel(level);
    }

    private static class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord logRecord) {
            return new Date(logRecord.getMillis()).toString() + " " +
                    logRecord.getLevel() + ":" +
                    logRecord.getLoggerName() + " " +
                    String.format(logRecord.getMessage(), logRecord.getParameters()) + "\n";
        }
    }
}
