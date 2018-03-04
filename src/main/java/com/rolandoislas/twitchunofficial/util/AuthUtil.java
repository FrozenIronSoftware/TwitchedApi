/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.jetbrains.annotations.Nullable;
import spark.Request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class AuthUtil {
    private static List<String> ALLOWED_IDS;
    private static boolean authenticate;

    static {
        String[] allowedIds = System.getenv().getOrDefault("ALLOWED_CLIENT_ID", "").split(",");
        ALLOWED_IDS = new ArrayList<>(Arrays.asList(allowedIds));
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
        if (ALLOWED_IDS.isEmpty()) {
            Logger.warn("Missing environment variable: ALLOWED_CLIENT_ID");
            return false;
        }
        String header = request.headers("Client-ID");
        if (header == null || header.isEmpty() || !ALLOWED_IDS.contains(header)) {
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

    /**
     * Extract a Twitch token from a request.
     * Checks the headers first, then falls back to the queries
     * @param request request
     * @return token if found
     */
    @Nullable
    public static String extractTwitchToken(Request request) {
        String token = request.headers("Twitch-Token");
        // This is here to support versions that use the query param.
        // New versions should not use this as it is a security risk when the request URL is logged.
        if (token == null || token.isEmpty())
            token = request.queryParams("token");
        if (token == null || token.isEmpty())
            return null;
        return token;
    }

    /**
     * Hash a string with SHA1 and the specified salt
     * @param raw raw string
     * @param salt salt - the global salt will be used if this is null
     * @return hashed string
     */
    public static String hashString(@Nullable String raw, @Nullable String salt) {
        if (raw == null)
            raw = "";
        if (salt == null)
            salt = System.getenv().getOrDefault("SALT", "");
        String hash = Hashing.sha1().hashString(raw, Charsets.UTF_8).toString();
        hash = Hashing.sha1().hashString(hash + salt, Charsets.UTF_8).toString();
        return hash;
    }
}
