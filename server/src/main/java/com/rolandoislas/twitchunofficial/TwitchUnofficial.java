/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.rolandoislas.twitchunofficial.data.Constants;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.DatabaseUtil;
import com.rolandoislas.twitchunofficial.util.GoogleStorage;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.admin.TwitchedAdminServer;
import com.rolandoislas.twitchunofficial.util.admin.TwitchedGenHashServer;
import com.rolandoislas.twitchunofficial.util.admin.TwitchedStreamQualityServer;

import java.util.logging.Level;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

public class TwitchUnofficial {

    public static ApiCache cache;
    static GoogleStorage storage;
    static boolean redirectToHttps;
    static String queueId;

    public static void main(String[] args) {
        // Parse args
        String logLevel = System.getenv().getOrDefault("LOG_LEVEL", "INFO");
        // No auth check
        if (System.getenv().getOrDefault("TWITCH_NO_AUTH", "false").equalsIgnoreCase("true"))
            AuthUtil.setAuthenticate(false);
        // Init logger
        Logger.setLevel(Level.parse(logLevel));
        Logger.info("Starting Twitched %s", Constants.VERSION);
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
        String redisUrlEnv = System.getenv("REDIS_URL_ENV");
        String redisUrlEnvName = redisUrlEnv == null || redisUrlEnv.isEmpty() ? "REDIS_URL" : redisUrlEnv;
        String redisServer = System.getenv(redisUrlEnvName);
        if (redisServer == null || redisServer.isEmpty()) {
            Logger.warn("Missing env: %s", redisUrlEnvName);
            System.exit(1);
        }
        // SQL address
        String sqlUrlEnv = System.getenv("SQL_URL_ENV");
        String sqlUrlEnvName = sqlUrlEnv == null || sqlUrlEnv.isEmpty() ? "SQL_URL" : sqlUrlEnv;
        String sqlServer = System.getenv(sqlUrlEnvName);
        if (sqlServer == null || sqlServer.isEmpty()) {
            Logger.warn("Missing env: %s", sqlUrlEnvName);
            System.exit(1);
        }
        // Twitch details
        String twitchClientId = getenv("TWITCH_CLIENT_ID");
        String twitchClientSecret = System.getenv("TWITCH_CLIENT_SECRET");
        // Redirect
        redirectToHttps = Boolean.parseBoolean(System.getenv().getOrDefault("REDIRECT_HTTP", "true"));
        // Google Storage Credentials
        String googleStorageCredentials = System.getenv("GOOGLE_STORAGE_CREDENTIALS");
        if (googleStorageCredentials == null || googleStorageCredentials.isEmpty()) {
            Logger.warn("Missing env: GOOGLE_STORAGE_CREDENTIALS");
            System.exit(1);
        }
        // Queue id
        queueId = System.getenv("BIF_QUEUE_ID");
        if (queueId == null || queueId.isEmpty()) {
            Logger.warn("Missing env: BIF_QUEUE_ID");
            System.exit(1);
        }
        // Set values
        port(port);
        staticFiles.location("/static/");
        staticFiles.expireTime(604800); // One Week cache
        TwitchUnofficial.cache = new ApiCache(redisServer);
        TwitchUnofficialApi.init(twitchClientId, twitchClientSecret);
        DatabaseUtil.setServer(sqlServer);
        storage = new GoogleStorage(cache);
        // Global page rules
        before(TwitchUnofficialServer::handleGlobalPageRules);
        // API
        path("/api", () -> {
            before("/*", (request, response) -> response.type("application/json"));
            // Twitch
            path("/twitch", () -> {
                // Kraken
                path("/kraken", () -> {
                    path("/communities", () -> {
                        get("", TwitchUnofficialApi::getCommunityKraken);
                        get("/top", TwitchUnofficialApi::getCommunitiesKraken);
                    });
                    get("/search", TwitchUnofficialApi::getSearchKraken);
                    path("/users", () -> path("/follows", () -> {
                        get("/follow", TwitchUnofficialApi::followKraken);
                        get("/unfollow", TwitchUnofficialApi::unfollowKraken);
                    }));
                });
                // Helix
                path("/helix", () -> {
                    get("/streams", TwitchUnofficialApi::getStreamsHelix);
                    path("/games", () -> {
                        get("", TwitchUnofficialApi::getGamesHelix);
                        get("/top", TwitchUnofficialApi::getTopGamesHelix);
                    });
                    path("/users", () -> {
                        get("", TwitchUnofficialApi::getUsersHelix);
                        path("/follows", () -> {
                            get("", TwitchUnofficialApi::getUserFollowHelix);
                            get("/streams", TwitchUnofficialApi::getUserFollowedStreamsHelix);
                        });
                    });
                    get("/videos", TwitchUnofficialApi::getVideosHelix);
                });
                // Undocumented Twitch HLS endpoints
                get("/hls/*/*/*/*", TwitchUnofficialApi::getHlsData);
                get("/hls/*/*/*", TwitchUnofficialApi::getHlsData);
                get("/hls/*/*", TwitchUnofficialApi::getHlsData);
                get("/hls/*", TwitchUnofficialApi::getHlsData);
                get("/vod/*/*/*/*", TwitchUnofficialApi::getVodData);
                get("/vod/*/*/*", TwitchUnofficialApi::getVodData);
                get("/vod/*/*", TwitchUnofficialApi::getVodData);
                get("/vod/*", TwitchUnofficialApi::getVodData);
                // Undocumented game follows endpoint
                path("/games", () -> {
                    get("/follows", TwitchUnofficialApi::getFollowedGames);
                    get("/follow", TwitchUnofficialApi::followGame);
                    get("/unfollow", TwitchUnofficialApi::unfollowGame);
                    get("/following", TwitchUnofficialApi::getFollowingGame);
                });
            });
            // Twitched
            path("/link", () -> {
                get("", TwitchedApi::getLinkId);
                post("", "application/json", TwitchedApi::postLinkToken);
                get("/status", TwitchedApi::getLinkStatus);
                get("/refresh", TwitchedApi::refreshToken);
                get("/validate", TwitchedApi::validateToken);
            });
            //noinspection CodeBlock2Expr
            path("/dev", () -> {
                get("/log", TwitchedApi::logGet);
                get("/stall", TwitchedApi::getStall);
            });
            //noinspection CodeBlock2Expr
            path("/ad", () -> {
                get("/server", TwitchedApi::getAdServer);
            });
            path("/communities", () -> {
                get("/follows", TwitchedApi::getFollowedCommunities);
                get("/follow", TwitchedApi::followCommunity);
                get("/unfollow", TwitchedApi::unfollowCommunity);
            });
            get("/config", TwitchedApi::getTwitchedConfig);
            path("/qualities", () -> {
                get("", TwitchedApi::getStreamQualitiesForModels);
                post("", TwitchedApi::postStreamQualitiesForModels);
            });
            get("/bif/*/*", TwitchedApi::getBifUrl);
        });
        // Web
        get("/", TwitchUnofficialServer::getIndex);
        path("/link", () -> {
            get("", TwitchUnofficialServer::getLinkIndex);
            get("/complete", TwitchUnofficialServer::getLinkCallback);
        });
        path("/info", () -> {
            get("", TwitchUnofficialServer::getInfoIndex);
            get("/oss", TwitchUnofficialServer::getInfoOss);
            get("/privacy", TwitchUnofficialServer::getInfoPrivacy);
            get("/streamquality", TwitchUnofficialServer::getInfoStreamQuality);
        });
        get("/extension", TwitchUnofficialServer::getExtensionIndex);
        get("/support", TwitchUnofficialServer::getSupportIndex);
        get("/app", TwitchUnofficialServer::getAppIndex);
        // Admin
        path("/admin", () -> {
            get("/login", TwitchedAdminServer::getLoginPage);
            get("/logout", TwitchedAdminServer::getLogoutPage);
            path("/genhash", () -> {
                get("", TwitchedGenHashServer::getIndex);
                post("", TwitchedGenHashServer::postCredentials);
            });
            path("/streamquality", () -> {
                get("", TwitchedStreamQualityServer::getIndex);
            });
            // API
            path("/api", () -> {
                post("/login", TwitchedAdminServer::postLoginPage);
            });
        });
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
