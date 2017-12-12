/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import spark.Request;


public class AuthUtil {
    private static final String ALLOWED_ID;
    private static boolean authenticate;

    static {
        ALLOWED_ID = System.getenv("ALLOWED_ROKU_ID");
        authenticate = true;
    }

    /**
     * Check headers for Roku ID and match it against the allowed ID
     * @param request request to check
     * @return has valid header/ID
     */
    public static boolean verify(Request request) {
        if (!authenticate)
            return true;
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

    /**
     * Set if the auth util should authenticate requests
     * @param authenticate do auth?
     */
    public static void setAuthenticate(boolean authenticate) {
        if (!authenticate)
            Logger.warn("Requests will not be authenticated!");
        AuthUtil.authenticate = authenticate;
    }
}
