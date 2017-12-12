/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.Logger;
import spark.Filter;

import java.util.logging.Level;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

public class TwitchUnofficial {

    public static ApiCache cache;

    public static void main(String[] args) {
        Logger.setLevel(Level.ALL);
        Logger.info("Starting");
        // Parse port
        int port = 5000;
        String portString = System.getenv("PORT");
        try {
            if (portString != null && !portString.isEmpty())
                port = Integer.parseInt(portString);
        }
        catch (NumberFormatException e) {
            Logger.warn("Failed to parse PORT env var: %s", portString);
        }
        // Redis address
        String redisServer = getenv("REDIS_URL");
        // Twitch details
        String twitchClientId = getenv("TWITCH_CLIENT_ID");
        String twitchClientSecret = getenv("TWITCH_CLIENT_SECRET");
        String twitchToken = getenv("TWITCH_TOKEN");
        // Set values
        port(port);
        staticFiles.location("/static/");
        TwitchUnofficial.cache = new ApiCache(redisServer);
        TwitchUnofficialApi.init(twitchClientId, twitchClientSecret, twitchToken);
        // Redirect paths with a trailing slash
        before((Filter) (request, response) -> {
            if (request.pathInfo().endsWith("/") && !request.pathInfo().equals("/"))
                response.redirect(request.pathInfo().substring(0, request.pathInfo().length() - 1));
        });
        // API
        path("/api", () -> {
            before("/*", (request, response) -> response.type("application/json"));
            path("/twitch", () -> {
                get("/streams", TwitchUnofficialApi::getStreams);
                get("/stream/hls", TwitchUnofficialApi::getHlsData);
            });
        });
        // Index
        get("/", TwitchUnofficialServer::getIndex);
    }

    /**
     * Get an environment variable or log and die
     * @param name env var
     */
    private static String getenv(String name) {
        String env = System.getenv(name);
        if (env == null || env.isEmpty()) {
            Logger.warn("Missing required environment variable: %s", name);
            System.exit(1);
        }
        return env;
    }
}
