/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import spark.Request;


public class AuthUtil {
    private static final String ALLOWED_ID;

    static {
        ALLOWED_ID = System.getenv("ALLOWED_ROKU_ID");
    }

    /**
     * Check headers for Roku ID and match it against the allowed ID
     * @param request request to check
     * @return has valid header/ID
     */
    public static boolean verify(Request request) {
        if (ALLOWED_ID == null || ALLOWED_ID.isEmpty()) {
            Logger.warn("Missing environment variable: ALLOWED_ROKU_ID");
            return false;
        }
        String header = request.headers("X-Roku-Reserved-Dev-Id");
        if (header == null || !header.equals(AuthUtil.ALLOWED_ID)) {
            Logger.warn(
                    String.format(
                            "Received an unauthenticated request:\n\tIP %s\n\tUser Agent: %s\n\tID: %s",
                            request.ip(),
                            request.userAgent(),
                            header == null ? "" : header
                    ));
            return false;
        }
        return true;
    }
}
