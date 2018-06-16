/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.goebl.david.Webb;
import com.goebl.david.WebbException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.Constants;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.data.json.AdServer;
import com.rolandoislas.twitchunofficial.data.json.AdServerList;
import com.rolandoislas.twitchunofficial.data.json.CfVisitor;
import com.rolandoislas.twitchunofficial.data.json.LinkId;
import com.rolandoislas.twitchunofficial.data.json.LinkToken;
import com.rolandoislas.twitchunofficial.data.model.FollowQueue;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.DatabaseUtil;
import com.rolandoislas.twitchunofficial.util.HeaderUtil;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.StringUtil;
import com.rolandoislas.twitchunofficial.util.twitch.AccessToken;
import com.rolandoislas.twitchunofficial.util.twitch.TokenValidation;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import com.rolandoislas.twitchunofficial.util.twitch.kraken.Community;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.BAD_REQUEST;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.checkAuth;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.gson;
import static com.rolandoislas.twitchunofficial.TwitchUnofficialApi.halt;

class TwitchedApi {
    private static final String OAUTH_CALLBACK_PATH = "/link/complete";
    private static Random random = new Random();

    /**
     * Generate a new ID for a device to begin linking
     * @param request request
     * @param response response
     * @return json id
     */
    @Cached
    static String getLinkId(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        String type = request.queryParams("type");
        String id = request.queryParams("id");
        // Check params
        if (type == null || (!type.equals("roku") && !type.equals("ATV")))
            throw halt(BAD_REQUEST, "Type is invalid");
        if (id == null || id.isEmpty())
            throw halt(BAD_REQUEST, "Id is empty");
        // Check cache
        String linkCacheId = ApiCache.createKey(ApiCache.LINK_PREFIX, type, id);
        cache.remove(linkCacheId);
        // Generate new ID
        String linkId;
        String usableChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        do {
            linkId = new Random().ints(6, 0, usableChars.length())
                    .mapToObj(i -> "" + usableChars.charAt(i))
                    .collect(Collectors.joining()).toUpperCase();
        }
        while (cache.containsLinkId(linkId));
        String shortLinkCacheId = ApiCache.createKey(ApiCache.LINK_PREFIX, linkId);
        // Store and return
        LinkId ret = new LinkId(linkId, getLinkIdVersionFromHeader(request));
        String retJson = gson.toJson(ret);
        cache.set(linkCacheId, retJson);
        cache.set(shortLinkCacheId, linkCacheId);
        return retJson;
    }

    /**
     * Get the version of link id to use depending on Twitched version header
     * @param request web request
     * @return version 1 (implicit) or 2 (authorization)
     */
    private static int getLinkIdVersionFromHeader(Request request) {
        @NotNull ComparableVersion version = HeaderUtil.extractVersion(request);
        return version.compareTo(new ComparableVersion("1.4")) >= 0 ? 2 : 1;
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
    static String getLinkStatus(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        String type = request.queryParams("type");
        String id = request.queryParams("id");
        // Check params
        if (type == null || (!type.equals("roku") && !type.equals("ATV")))
            throw halt(BAD_REQUEST, "Type is invalid");
        if (id == null || id.isEmpty())
            throw halt(BAD_REQUEST, "Id is empty");
        // Construct return json object
        JsonObject ret = new JsonObject();
        // Check cache for link id
        String linkCacheId = ApiCache.createKey(ApiCache.LINK_PREFIX, type, id);
        // Parse json
        LinkId linkId = new LinkId(cache.get(linkCacheId), gson);
        if (linkId.getLinkId() == null) {
            ret.addProperty("error", TwitchUnofficialApi.NOT_FOUND);
            return ret.toString();
        }
        // Check cache for token
        String tokenCacheKey = ApiCache.createKey(ApiCache.TOKEN_PREFIX, linkId.getLinkId());
        String token = cache.get(tokenCacheKey);
        if (token == null) {
            ret.addProperty("complete", false);
            return ret.toString();
        }
        AccessToken accessToken;
        try {
            accessToken = gson.fromJson(token, AccessToken.class);
        }
        catch (JsonSyntaxException e) {
            throw halt(TwitchUnofficialApi.SERVER_ERROR, "Server error: Invalid token json");
        }
        ret = accessToken.toJsonObject();
        return ret.toString();
    }

    /**
     * Check if a link code is valid (e.g. waiting for a token)
     * @param linkId code to check
     * @return is valid
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isLinkCodeValid(String linkId) {
        return cache.containsLinkId(linkId.toUpperCase());
    }

    /**
     * Redirect to the twitch Oauth endpoint
     * @param linkId id to use
     * @param request request
     * @param response response
     * @return string
     */
    static String redirectToTwitchOauth(String linkId, Request request, Response response) {
        // Get link id version
        String shortLinkCacheId = ApiCache.createKey(ApiCache.LINK_PREFIX, linkId.toUpperCase());
        String linkCacheId = cache.get(shortLinkCacheId);
        if (linkCacheId == null)
            throw Spark.halt(TwitchUnofficialApi.NOT_FOUND, "Link id not found");
        LinkId linkIdObj = new LinkId(cache.get(linkCacheId), gson);
        if (linkIdObj.getLinkId() == null)
            throw Spark.halt(TwitchUnofficialApi.NOT_FOUND, "Link id not found");
        String oauthUrl = "https://id.twitch.tv/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=%s" +
                "&scope=%s&force_verify=%s&state=%s";
        oauthUrl = String.format(
                oauthUrl, 
                TwitchUnofficialApi.getTwitchCredentials().getClientId(),
                getRedirectUrl(request),
                linkIdObj.getVersion() == 1 ? "token" : "code",
                "chat_login+user_follows_edit+user_subscriptions",
                "true",
                linkId.toUpperCase()
        );
        response.redirect(oauthUrl);
        return null;
    }

    /**
     * Get redirect url
     * @param request http request
     * @return redirect url
     */
    private static String getRedirectUrl(Request request) {
        String redirectUrl = System.getenv("REDIRECT_URL");
        if (redirectUrl != null && !redirectUrl.isEmpty())
            return redirectUrl;
        String scheme = request.scheme();
        if (request.headers("X-Forwarded-Proto") != null)
            scheme = request.headers("X-Forwarded-Proto");
        if (request.headers("CF-Visitor") != null) {
            try {
                CfVisitor cfVisitor = gson.fromJson(request.headers("CF-Visitor"), CfVisitor.class);
                if (cfVisitor != null && cfVisitor.getScheme() != null)
                    scheme = cfVisitor.getScheme();
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        return scheme + "://" + request.host() + OAUTH_CALLBACK_PATH;
    }

    /**
     * Handle a post request for a token
     * @param request request
     * @param response response
     * @return empty string
     */
    static String postLinkToken(Request request, @SuppressWarnings("unused") Response response) {
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
        String tokenCacheKey = ApiCache.createKey(ApiCache.TOKEN_PREFIX, linkId);
        AccessToken accessToken = new AccessToken(token, null);
        cache.set(tokenCacheKey, gson.toJson(accessToken));
        // Return
        return "200";
    }

    /**
     * Request an access token from Twitch with an authorization code
     * @param request http request
     * @param authCode authentication code from Twitch
     * @param state linkId
     * @return if the token fetch was successful
     */
    static boolean requestAccessToken(Request request, String authCode, String state) throws WebbException {
        if (!isLinkCodeValid(state.toUpperCase()))
            return false;
        String url = "https://id.twitch.tv/oauth2/token?client_id=%s&client_secret=%s&code=%s" +
                "&grant_type=%s&redirect_uri=%s";
        url = String.format(
                url,
                TwitchUnofficialApi.getTwitchCredentials().getClientId(),
                TwitchUnofficialApi.getTwitchCredentials().getClientSecret(),
                authCode,
                "authorization_code",
                getRedirectUrl(request)
        );
        AccessToken accessToken = requestAccessTokenFromUrl(url);
        String tokenCacheKey = ApiCache.createKey(ApiCache.TOKEN_PREFIX, state.toUpperCase());
        cache.set(tokenCacheKey, gson.toJson(accessToken));
        return true;
    }

    /**
     * Request an access token from twitch via the provided url
     * @param url fully specified URL that will return and access token
     * @return access token, or null on error
     */
    @Nullable
    private static AccessToken requestAccessTokenFromUrl(String url) {
        com.goebl.david.Response<String> result;
        try {
            Webb webb = getWebb();
            result = webb
                    .post(url)
                    .ensureSuccess()
                    .asString();
        }
        catch (WebbException e) {
            Logger.exception(e);
            return null;
        }
        AccessToken accessToken;
        try {
            accessToken = gson.fromJson(result.getBody(), AccessToken.class);
        }
        catch (JsonSyntaxException e) {
            Logger.exception(e);
            return null;
        }
        return accessToken;
    }

    /**
     * Attempt to refresh a Twitch token
     * @param request request
     * @param response response
     * @return json - always 200 status code with "error" field set to true on error
     */
    static String refreshToken(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        String refreshToken = request.queryParams("refresh_token");
        String scope = request.queryParams("scope");
        JsonObject ret = new JsonObject();
        if (refreshToken == null || refreshToken.isEmpty() || scope == null || scope.isEmpty()) {
            ret.addProperty("error", true);
            ret.addProperty("message", "Missing refresh token or scope");
            return ret.toString();
        }
        String url = "https://id.twitch.tv/oauth2/token?client_id=%s&client_secret=%s&grant_type=%s&refresh_token=%s" +
                "&scope=%s";
        try {
            url = String.format(
                    url,
                    URLEncoder.encode(TwitchUnofficialApi.getTwitchCredentials().getClientId(), "UTF-8"),
                    URLEncoder.encode(TwitchUnofficialApi.getTwitchCredentials().getClientSecret(), "UTF-8"),
                    "refresh_token",
                    URLEncoder.encode(refreshToken, "UTF-8"),
                    URLEncoder.encode(scope.replace("+", " "), "UTF-8")
            );
        }
        catch (UnsupportedEncodingException e) {
            Logger.exception(e);
        }
        AccessToken accessToken = requestAccessTokenFromUrl(url);
        if (accessToken == null) {
            ret.addProperty("error", true);
            ret.addProperty("message", "Failed fetching access token");
            return ret.toString();
        }
        return accessToken.toJsonObject().toString();
    }

    /**
     * Call the Twitch Oauth validate endpoint to check a user token
     * @param request request
     * @param response response
     * @return json output from the validate endpoint
     */
    static String validateToken(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        List<TokenValidation> validationList = new ArrayList<>(); // Wrapped in an array to be compatible with Twitched
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            return gson.toJson(validationList);
        String url = "https://id.twitch.tv/oauth2/validate";
        com.goebl.david.Response<String> result;
        try {
            Webb webb = getWebb();
            result = webb
                    .get(url)
                    .header("Authorization", "OAuth " + token)
                    .ensureSuccess()
                    .asString();
        }
        catch (WebbException e) {
            Logger.exception(e);
            return gson.toJson(validationList);
        }
        TokenValidation tokenValidation;
        try {
            tokenValidation = gson.fromJson(result.getBody(), TokenValidation.class);
        }
        catch (JsonSyntaxException e) {
            Logger.exception(e);
            return gson.toJson(validationList);
        }
        if (tokenValidation != null)
            validationList.add(tokenValidation);
        // Start a cache follows request
        String userId = tokenValidation != null ? tokenValidation.getUserId() : null;
        if (userId != null && !userId.isEmpty()) {
            cache.cacheUserIdFromToken(userId, token);
            TwitchUnofficialApi.cacheFollows(userId, FollowQueue.FollowType.CHANNEL);
            TwitchUnofficialApi.cacheFollows(tokenValidation.getLogin(), FollowQueue.FollowType.GAME);
        }
        // Return validation data
        return gson.toJson(validationList);
    }

    /**
     * Log any headers and query params
     * @param request request
     * @param response response
     * @return 404
     */
    @Nullable
    static String logGet(Request request, @SuppressWarnings("unused") Response response) {
        if (!isDevApiEnabled())
            return null;
        Logger.debug("-----HEADERS-----");
        for (String header : request.headers())
            Logger.debug("%s: %s", header, request.headers(header));
        Logger.debug("-----QUERY PARAMS-----");
        for (String queryParam : request.queryParams())
            Logger.debug("%s: %s", queryParam, request.headers(queryParam));
        return null;
    }

    /**
     * Check if the dev api is enabled
     * @return enabled
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isDevApiEnabled() {
        return System.getenv().getOrDefault("DEV_API", "").equalsIgnoreCase("true");
    }

    /**
     * Return an ad server url
     * @param request request
     * @param response response
     * @return json
     */
    static String getAdServer(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        // Params
        String type = request.queryParams("type");
        if (type == null || !type.equals("roku"))
            throw halt(BAD_REQUEST, "Invalid type");
        JsonObject adServerResponse = new JsonObject();
        String adServer = "";
        String adServerString = System.getenv("AD_SERVER");
        AdServerList adServerList = null;
        if (adServerString == null) {
            Logger.warn("Missing environment variable: AD_SERVER");
        }
        else {
            try {
                adServerList = gson.fromJson(adServerString, AdServerList.class);
            }
            catch (JsonSyntaxException e) {
                Logger.warn("Failed to parse ad server list from environment variable");
                Logger.exception(e);
            }
        }
        if (adServerList != null && adServerList.getAdServers() != null &&
            adServerList.getAdServers().size() > 0) {
            // Check region
            String region = request.headers("CF-IPCountry");
            if (region == null || region.isEmpty())
                region = "XX";
            List<AdServer> compatibleAdServers = new ArrayList<>();
            for (AdServer adServerJson : adServerList.getAdServers()) {
                if (adServerJson.getCountries() == null)
                    continue;
                for (String country : adServerJson.getCountries()) {
                    if (country != null && (country.equals(region) || country.equals("INT")) &&
                            adServerJson.getUrl() != null) {
                        compatibleAdServers.add(adServerJson);
                        break;
                    }
                }
            }
            if (compatibleAdServers.size() > 0) {
                int adServerIndex = TwitchedApi.random.nextInt(compatibleAdServers.size());
                AdServer selectedAdServer = compatibleAdServers.get(adServerIndex);
                adServer = selectedAdServer.getUrl();
            }
        }
        // Send response
        adServerResponse.addProperty("ad_server", adServer);
        return adServerResponse.toString();
    }

    /**
     * Stall the request for a certain amount of time
     * @param request request
     * @param response response
     * @return 404
     */
    static String getStall(Request request, @SuppressWarnings("unused") Response response) {
        if (!isDevApiEnabled())
            return null;
        int sleep = 30;
        String sleepString = request.queryParamOrDefault("delay", "30");
        UUID uuid = UUID.randomUUID();
        Logger.debug("Starting /api/dev/stall for %d seconds with UUID %s", sleep, uuid.toString());
        try {
            sleep = Integer.parseInt(sleepString);
        }
        catch (NumberFormatException e) {
            Logger.exception(e);
        }
        try {
            Thread.sleep(sleep);
        }
        catch (InterruptedException e) {
            Logger.exception(e);
        }
        Logger.debug("Finished /api/dev/stall for %d seconds with UUID %s", sleep, uuid.toString());
        return null;
    }

    /**
     * Create a webb instance with user agent set
     * @return Webb instance
     */
    static Webb getWebb() {
        Webb webb = Webb.create();
        webb.setDefaultHeader("Accept", "*/*");
        webb.setDefaultHeader("User-Agent",
                String.format("%s/%s (Java/%s)",
                        Constants.NAME,
                        Constants.VERSION,
                        System.getProperty("java.version")));
        return webb;
    }

    /**
     * Get user followed communities
     * @param request request
     * @param response response
     * @return json
     */
    static String getFollowedCommunities(Request request, spark.Response response) {
        checkAuth(request);
        // Params
        String limit = request.queryParamOrDefault("limit", "20");
        String offset = request.queryParamOrDefault("offset", "0");
        String toId = request.queryParams("to_id");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            TwitchUnofficialApi.unauthorized();
        // Parse
        int limitInt = (int) StringUtil.parseLong(limit);
        int offsetInt = (int) StringUtil.parseLong(offset);
        if (limitInt > 100 || limitInt < 1)
            throw halt(BAD_REQUEST, "Limit out of range: 1 - 100");
        // User
        @Nullable User user = TwitchUnofficialApi.getUserFromToken(token);
        if (user == null || user.getId() == null || user.getId().isEmpty())
            throw halt(TwitchUnofficialApi.SERVER_ERROR, "Failed to get user id.");
        // Database
        List<Community> communities = DatabaseUtil.getUserFollowedCommunities(user.getId(), limitInt, offsetInt, toId);
        if (communities == null)
            throw halt(TwitchUnofficialApi.SERVER_ERROR, "");
        // Check cache
        return gson.toJson(communities);
    }

    /**
     * Follow a community
     * @param request request
     * @param response response
     * @return json
     */
    @NotNull
    static String followCommunity(Request request, spark.Response response) {
        return setFollowCommunity(request, true);
    }

    /**
     * Follow or unfollow a community for a user id
     * @param request request
     * @param setFollow should set following or delete
     * @return empty json object
     */
    @NotNull
    private static String setFollowCommunity(Request request, boolean setFollow) {
        checkAuth(request);
        // Params
        String id = request.queryParams("id");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            TwitchUnofficialApi.unauthorized();
        // Parse
        try {
            //noinspection ResultOfMethodCallIgnored
            UUID.fromString(id);
        }
        catch (IllegalArgumentException e) {
            throw halt(BAD_REQUEST, "Invalid community id");
        }
        // User
        @Nullable User user = TwitchUnofficialApi.getUserFromToken(token);
        if (user == null || user.getId() == null || user.getId().isEmpty())
            throw halt(TwitchUnofficialApi.SERVER_ERROR, "Failed to get user id.");
        // Update community
        if (setFollow) {
            Community community = DatabaseUtil.getCommunity(id);
            if (community == null ||
                    System.currentTimeMillis() - community.getModified() >= ApiCache.TIMEOUT_DAY * 1000) {
                community = TwitchUnofficialApi.getCommunityKraken(null, id);
                if (community != null)
                    if (!DatabaseUtil.cacheCommunity(community))
                        Logger.debug("Failed to cache community");
            }
        }
        // Database
        if (!DatabaseUtil.setUserFollowCommunity(user.getId(), id, setFollow))
            throw halt(TwitchUnofficialApi.SERVER_ERROR, "");
        return "{}";
    }

    /**
     * Unfollow a community
     * @param request request
     * @param response response
     * @return json
     */
    static String unfollowCommunity(Request request, spark.Response response) {
        return setFollowCommunity(request, false);
    }

    /**
     * Return configuration json
     * @param request request
     * @param response response
     * @return json
     */
    static String getTwitchedConfig(Request request, Response response) {
        checkAuth(request);
        return System.getenv().getOrDefault("TWITCHED_CONFIG", "{}");
    }
}
