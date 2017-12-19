/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.twitch.LinkToken;
import net.jodah.expiringmap.ExpiringMap;
import spark.Request;
import spark.Response;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.BAD_REQUEST;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.checkAuth;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.gson;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.halt;

class TwitchedApi {

    private static final Map<String, String> links;
    private static final Map<String, String> tokens;
    private static final String OAUTH_CALLBACK_PATH = "/link/complete";

    static {
        links = ExpiringMap.builder()
                .maxSize(1000)
                .expiration(5, TimeUnit.MINUTES)
                .build();
        tokens = ExpiringMap.builder()
                .maxSize(1000)
                .expiration(1, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Generate a new ID for a device to begin linking
     * @param request request
     * @param response response
     * @return json id
     */
    @Cached
    static String getLinkId(Request request, Response response) {
        checkAuth(request);
        String type = request.queryParams("type");
        String id = request.queryParams("id");
        // Check params
        if (type == null || !type.equals("roku"))
            throw halt(BAD_REQUEST, "Type is invalid");
        if (id == null || id.isEmpty())
            throw halt(BAD_REQUEST, "Id is empty");
        // Construct return json object
        JsonObject ret = new JsonObject();
        // Check cache
        String requestId = ApiCache.createKey("link", type, id);
        if (links.containsKey(requestId))
            links.remove(requestId);
        // Generate new ID
        String linkId;
        String usableChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        do {
            linkId = new Random().ints(6, 0, usableChars.length())
                    .mapToObj(i -> "" + usableChars.charAt(i))
                    .collect(Collectors.joining()).toUpperCase();
        }
        while (links.containsValue(linkId));
        // Store and return
        links.put(requestId, linkId);
        ret.addProperty("id", linkId);
        return ret.toString();
    }

    /**
     * Get the status of a link.
     * If the link has been completed (i.e. user logged in and there is a valid token) the completed field will be true
     * and the token field will be populated and present
     * @param request request
     * @param response reponse
     * @return link status json
     */
    @NotCached
    static String getLinkStatus(Request request, Response response) {
        checkAuth(request);
        String type = request.queryParams("type");
        String id = request.queryParams("id");
        // Check params
        if (type == null || !type.equals("roku"))
            throw halt(BAD_REQUEST, "Type is invalid");
        if (id == null || id.isEmpty())
            throw halt(BAD_REQUEST, "Id is empty");
        // Construct return json object
        JsonObject ret = new JsonObject();
        // Check cache for link id
        String requestId = ApiCache.createKey("link", type, id);
        if (!links.containsKey(requestId)) {
            ret.addProperty("error", 404);
            return ret.toString();
        }
        String linkId = links.get(requestId);
        // Check cache for token
        String token = tokens.get(linkId);
        if (token == null) {
            ret.addProperty("complete", false);
            return ret.toString();
        }
        ret.addProperty("complete", true);
        ret.addProperty("token", token);
        return ret.toString();
    }

    /**
     * Check if a link code is valid (e.g. waiting for a token)
     * @param linkId code to check
     * @return is valid
     */
    static boolean isLinkCodeValid(String linkId) {
        return links.containsValue(linkId.toUpperCase());
    }

    /**
     * Redirect to the twitch Oauth endpoint
     * @param linkId id to use
     * @param request request
     * @param response response
     * @return string
     */
    static String redirectToTwitchOauth(String linkId, Request request, Response response) {
        String oauthUrl = "https://api.twitch.tv/kraken/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=%s" +
                "&scope=%s&force_verify=%s&state=%s";
        oauthUrl = String.format(
                oauthUrl, 
                TwitchUnofficialApi.twitch.getClientId(),
                request.scheme() + "://" + request.host() + OAUTH_CALLBACK_PATH,
                "token",
                "",
                "true",
                linkId.toUpperCase()
        );
        response.redirect(oauthUrl);
        return null;
    }

    /**
     * Handle a post request for a token
     * @param request request
     * @param response response
     * @return empty string
     */
    static String postLinkToken(Request request, Response response) {
        // Parse body
        LinkToken linkToken;
        try {
            linkToken = gson.fromJson(request.body(), LinkToken.class);
        }
        catch (JsonSyntaxException e) {
            throw halt(BAD_REQUEST, "Invalid body");
        }
        String token = linkToken.getToken();
        String linkId = linkToken.getId();
        if (token == null || token.isEmpty())
            throw halt(BAD_REQUEST, "Invalid token");
        if (linkId == null || linkId.isEmpty() || !isLinkCodeValid(linkId))
            throw halt(BAD_REQUEST, "Invalid link id");
        linkId = linkId.toUpperCase();
        // Save token
        tokens.put(linkId, token);
        // Return
        return "200";
    }
}
