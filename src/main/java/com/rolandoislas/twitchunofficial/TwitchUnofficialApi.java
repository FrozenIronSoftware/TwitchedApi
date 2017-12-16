/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.Id;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.twitch.streamlink.StreamList;
import com.rolandoislas.twitchunofficial.util.twitch.streamlink.StreamlinkData;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Pagination;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserName;
import me.philippheuer.twitch4j.TwitchClient;
import me.philippheuer.twitch4j.TwitchClientBuilder;
import me.philippheuer.twitch4j.auth.model.OAuthCredential;
import me.philippheuer.twitch4j.enums.Endpoints;
import me.philippheuer.twitch4j.exceptions.RestException;
import me.philippheuer.twitch4j.model.Community;
import me.philippheuer.twitch4j.model.CommunityList;
import me.philippheuer.twitch4j.model.Stream;
import me.philippheuer.twitch4j.model.TopGame;
import me.philippheuer.twitch4j.model.TopGameList;
import me.philippheuer.util.rest.QueryRequestInterceptor;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

public class TwitchUnofficialApi {

    private static final int BAD_REQUEST = 400;
    private static final int SERVER_ERROR =  503;
    private static final int BAD_GATEWAY = 502;
    private static final String API = "https://api.twitch.tv/helix";
    private static TwitchClient twitch;
    private static Gson gson;
    private static OAuthCredential twitchOauth;

    /**
     * Send a JSON error message to the current requester
     * @param code HTTP status code
     * @param message error message
     * @return halt
     */
    @NotCached
    private static HaltException halt(int code, String message) {
        JsonObject jsonObject = new JsonObject();
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        jsonObject.add("error", error);
        throw Spark.halt(code, jsonObject.toString());
    }

    /**
     * Helper to send 401 unauthorized
     */
    @NotCached
    private static void unauthorized() {
        throw halt(401, "Unauthorized");
    }

    /**
     * Helper to check authentication headers
     * @param request request to check
     */
    @NotCached
    private static void checkAuth(Request request) {
        if (!AuthUtil.verify(request))
            unauthorized();
    }

    /**
     * Get stream data from twitch
     * @param request request
     * @param response response
     * @return stream data
     */
    @Cached
    static String getStreamsKraken(Request request, Response response) {
        checkAuth(request);
        Long limit = null;
        Long offset = null;
        String language;
        me.philippheuer.twitch4j.model.Game game = null;
        String channel;
        String streamType;
        try {
            limit = Long.parseLong(request.queryParams("limit"));
        }
        catch (NumberFormatException ignore) {}
        try {
            offset = Long.parseLong(request.queryParams("offset"));
        }
        catch (NumberFormatException ignore) {}
        language = request.queryParams("language");
        String gameString = request.queryParams("game");
        if (gameString != null) {
            game = new me.philippheuer.twitch4j.model.Game();
            game.setName(gameString);
        }
        channel = request.queryParams("channel");
        streamType = request.queryParams("type");
        // Check cache
        String requestId = ApiCache.createKey("kraken/streams", limit, offset, language, game != null ? game.getName() : null,
                channel, streamType);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live
        List<Stream> streams = twitch.getStreamEndpoint().getAll(
                Optional.ofNullable(limit),
                Optional.ofNullable(offset),
                Optional.ofNullable(language),
                Optional.ofNullable(game),
                Optional.ofNullable(channel),
                Optional.ofNullable(streamType)
        );
        if (streams == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get stream HLS m3u8 links
     * @param request request
     * @param response response
     * @return links
     */
    @Cached
    static String getHlsData(Request request, Response response) {
        checkAuth(request);
        response.type("audio/mpegurl");
        if (request.splat().length < 1)
            throw Spark.halt(404);
        String fileName = request.splat()[0];
        String[] split = fileName.split("\\.");
        if (split.length < 2 || !split[1].equals("m3u8"))
            throw Spark.halt(404);
        String username = split[0];
        // Check cache
        String requestId = ApiCache.createKey("hls", username);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data
        StreamlinkData streamlinkData = null;
        try {
            Process streamlink = new ProcessBuilder(
                    "streamlink",
                    "--quiet",
                    "--json",
                    String.format("twitch.tv/%s", username)).start();
            streamlink.waitFor();
            Scanner scanner = new Scanner(streamlink.getInputStream()).useDelimiter("\\A");
            if (scanner.hasNext())
                streamlinkData = gson.fromJson(scanner.next(), StreamlinkData.class);
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            Logger.warn("Failed to call streamlink");
            Logger.exception(e);
            throw halt(SERVER_ERROR, "Failed to fetch data");
        }
        if (streamlinkData == null)
            throw halt(SERVER_ERROR, "Failed to fetch data");

        // Generate the master playlist
        StreamList streamList = streamlinkData.getStreams();
        String master = "#EXTM3U\n#EXT-X-VERSION:3\n";
        String playlist = "#EXT-X-STREAM-INF:BANDWIDTH=%d,FRAME-RATE=%d\n%s\n";
        if (streamList.get160p() != null)
            master += String.format(playlist, 400 * 1000, 30, streamList.get160p().getUrl());
        if (streamList.get360p() != null)
            master += String.format(playlist, 600 * 1000, 30, streamList.get360p().getUrl());
        if (streamList.get480p() != null)
            master += String.format(playlist, 1000 * 1000, 30, streamList.get480p().getUrl());
        if (streamList.get480p60() != null)
            master += String.format(playlist, 1500 * 1000, 30, streamList.get480p60().getUrl());
        if (streamList.get720p() != null)
            master += String.format(playlist, 2500 * 1000, 30, streamList.get720p().getUrl());
        if (streamList.get720p60() != null)
            master += String.format(playlist, 3500 * 1000, 60, streamList.get720p60().getUrl());
        if (streamList.get1080p() != null)
            master += String.format(playlist, 4000 * 1000, 30, streamList.get1080p().getUrl());
        if (streamList.get1080p60() != null)
            master += String.format(playlist, 4500 * 1000, 60, streamList.get1080p60().getUrl());
        // Cache and return
        cache.set(requestId, master);
        return master;
    }

    /**
     * Initialize the Twitch API wrapper
     * @param twitchClientId client id
     * @param twitchClientSecret secret
     * @param twitchToken token
     */
    @NotCached
    static void init(String twitchClientId, String twitchClientSecret, String twitchToken) {
        TwitchUnofficialApi.twitch = TwitchClientBuilder.init()
                .withClientId(twitchClientId)
                .withClientSecret(twitchClientSecret)
                .withCredential(twitchToken)
                .build();
        TwitchUnofficialApi.gson = new Gson();
        // Set credential
        for (Map.Entry<String, OAuthCredential>credentialEntry :
                twitch.getCredentialManager().getOAuthCredentials().entrySet()) {
            twitchOauth = credentialEntry.getValue();
        }
        if (twitchOauth == null)
            Logger.warn("No Oauth token provided. Requests will be rate limited to 30 per minute.");
    }

    /**
     * Get a list of games
     * @param request request
     * @param response response
     * @return games json
     */
    @Cached
    static String getGamesKraken(Request request, Response response) {
        checkAuth(request);
        // Parse parameters
        String limit = request.queryParamOrDefault("limit", "10");
        String offset = request.queryParamOrDefault("offset", "0");
        // Check cache
        String requestId = ApiCache.createKey("kraken/games", limit, offset);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Fetch live data
        String requestUrl = String.format("%s/games/top", Endpoints.API.getURL());
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("limit", limit));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("offset", offset));
        // REST Request
        List<TopGame> games = null;
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            TopGameList responseObject = restTemplate.getForObject(requestUrl, TopGameList.class);
            if (responseObject != null)
                games = responseObject.getTop();
        }
        catch (RestClientException | RestException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (games == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");

        // Store and return
        String json = gson.toJson(games);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get top communities
     * @param request request
     * @param response response
     * @return communities json
     */
    @Cached
    static String getCommunitiesKraken(Request request, Response response) {
        checkAuth(request);
        // Params
        Long limit = null;
        String cursor;
        try {
            limit = Long.parseLong(request.queryParams("limit"));
        }
        catch (NumberFormatException ignore) {}
        cursor = request.queryParams("cursor");
        // Check cache
        String requestId = ApiCache.createKey("kraken/communities/top", limit, cursor);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live
        CommunityList communities = twitch.getCommunityEndpoint()
                .getTopCommunities(Optional.ofNullable(limit), Optional.ofNullable(cursor));
        if (communities == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        String json = gson.toJson(communities);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get a specified community
     * @param request request
     * @param response response
     * @return community json
     */
    @Cached
    static String getCommunityKraken(Request request, Response response) {
        checkAuth(request);
        // Params
        String name = request.queryParams("name");
        String id = request.queryParams("id");
        if ((name == null || name.isEmpty()) && (id == null || id.isEmpty()))
            throw halt(BAD_REQUEST, "Bad Request: name or id is required");
        // Check cache
        String requestId = ApiCache.createKey("kraken/communities", name);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live
        Community community;
        if (name != null && !name.isEmpty())
            community = twitch.getCommunityEndpoint().getCommunityByName(name);
        else
            community = twitch.getCommunityEndpoint().getCommunityById(id);
        String json = gson.toJson(community);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get streams from the helix endpoint
     * @param request request
     * @param response response
     * @return stream json with usernames added to each stream as "user_name"
     */
    @Cached
    static String getStreamsHelix(Request request, Response response) {
        checkAuth(request);
        // Params
        String after = request.queryParams("after");
        String before = request.queryParams("before");
        String community = request.queryParams("community_id");
        String first = request.queryParamOrDefault("first", "20");
        String game = request.queryParams("game_id");
        String language = request.queryParams("language");
        String streamType = request.queryParamOrDefault("type", "all");
        String userId = request.queryParams("user_id");
        String userLogin = request.queryParams("user_login");
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParams("limit");
        // Set after based on offset
        if (offset != null) {
            try {
                long offsetLong = Long.parseLong(offset);
                long firstLong = Long.parseLong(first);
                Pagination pagination = new Pagination(
                        (offsetLong - 1) * firstLong,
                        (offsetLong + 1) * firstLong
                );
                after = pagination.getCursor();
            }
            catch (NumberFormatException ignore) {}
        }
        // Check cache
        String requestId = ApiCache.createKey("helix/streams", after, before, community, first, game, language,
                streamType, userId, userLogin, offset);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Request live

        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = null;
        // Endpoint
        String requestUrl = String.format("%s/streams", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Parameters
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("after", after));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("before", before));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("community_id", community));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("first", first));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("game_id", game));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("language", language));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", streamType));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("user_id", userId));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("user_login", userLogin));
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            try {
                com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList streamList = gson.fromJson(
                        responseObject.getBody(),
                        com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList.class);
                streams = streamList.getStreams();
            }
            catch (JsonSyntaxException ignore) {}
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException)
                Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            else
                Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (streams == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");

        // Add user names and game names to data
        List<String> gameIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streams) {
            userIds.add(stream.getUserId());
            gameIds.add(stream.getGameId());
        }
        Map<String, String> userNames = getUserNames(userIds);
        Map<String, String> gameNames = getGameNames(gameIds);
        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streams) {
            String userName = userNames.get(stream.getUserId());
            try {
                stream.setUserName(userName == null ? new UserName() : gson.fromJson(userName, UserName.class));
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                stream.setUserName(new UserName());
            }
            String gameName = gameNames.get(stream.getGameId());
            stream.setGameName(gameName == null ? "" : gameName);
        }

        // Cache and return
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get game name for ids, checking the cache first
     * @param gameIds ids
     * @return game name(value) and id(key)
     */
    @Cached
    private static Map<String, String> getGameNames(List<String> gameIds) {
        return getNameForIds(gameIds, Id.GAME);
    }

    /**
     * Get username for ids, checking the cache first
     * @param userIds ids
     * @return user names(value) and ids(key)
     */
    @Cached
    private static Map<String, String> getUserNames(List<String> userIds) {
        return getNameForIds(userIds, Id.USER);
    }

    /**
     * Get user name or game name for ids
     * @param ids user or game id - must be all one type
     * @param type type of ids
     * @return map <id, @Nullable name> all ids passed will be returned
     */
    @Cached
    private static Map<String, String> getNameForIds(List<String> ids, Id type) {
        // Get ids in cache
        Map<String, String> nameIdMap;
        switch (type) {
            case USER:
                nameIdMap = cache.getUserNames(ids);
                break;
            case GAME:
                nameIdMap = cache.getGameNames(ids);
                break;
            default:
                throw new IllegalArgumentException("Id type must be GAME or USER");
        }
        // Find missing ids
        List<String> missingIds = new ArrayList<>();
        for (Map.Entry<String, String> nameId : nameIdMap.entrySet())
            if (nameId.getValue() == null)
                missingIds.add(nameId.getKey());
        if (missingIds.size() == 0)
            return nameIdMap;
        // Request missing ids
        if (type.equals(Id.USER)) {
            List<User> users = getUsers(ids, null);
            if (users == null)
                throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
            // Store missing ids
            for (User user : users) {
                JsonObject name = new JsonObject();
                name.addProperty("display_name", user.getDisplayName());
                name.addProperty("login", user.getLogin());
                nameIdMap.put(user.getId(), name.toString());
            }
            cache.setUserNames(nameIdMap);
        }
        else if (type.equals(Id.GAME)) {
            List<Game> games = getGames(ids, null);
            if (games == null)
                throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
            // Store missing ids
            for (Game game : games)
                nameIdMap.put(game.getId(), game.getName());
            cache.setGameNames(nameIdMap);
        }
        return nameIdMap;
    }

    /**
     * Get games from Twitch API
     * @param ids id of games to fetch
     * @return games
     */
    @NotCached
    private static List<Game> getGames(@Nullable List<String> ids, @Nullable List<String> names) {
        if ((ids == null || ids.isEmpty()) && (names == null || names.isEmpty()))
            throw halt(BAD_REQUEST, "Bad request: missing game id or name");
        // Request live
        List<Game> games = null;
        // Endpoint
        String requestUrl = String.format("%s/games", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Parameters
        if (ids != null)
            for (String id : ids)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("id", id));
        if (names != null)
            for (String name : names)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("name", name));
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            try {
                GameList gameList = gson.fromJson(responseObject.getBody(), GameList.class);
                games = gameList.getGames();
            }
            catch (JsonSyntaxException ignore) {}
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException)
                Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            else
                Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        return games;
    }

    /**
     * Get a users from Twitch API
     * @param userIds id to poll
     * @return list of users
     */
    @NotCached
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames) {
        if ((userIds == null || userIds.isEmpty()) && (userNames == null || userNames.isEmpty()))
            throw halt(BAD_REQUEST, "Bad request: missing user id or user name");
        // Request live
        List<User> users = null;
        // Endpoint
        String requestUrl = String.format("%s/users", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Parameters
        if (userIds != null)
            for (String id : userIds)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("id", id));
        if (userNames != null)
            for (String name : userNames)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("login", name));
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            try {
                UserList userList = gson.fromJson(responseObject.getBody(), UserList.class);
                users = userList.getUsers();
            }
            catch (JsonSyntaxException ignore) {}
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException)
                Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            else
                Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        return users;
    }

    /**
     * Get games from the helix endpoint
     * @param request request
     * @param response response
     * @return game json
     */
    @Cached
    static String getGamesHelix(Request request, Response response) {
        // Parse query params
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String[] queryParams = request.queryString().split("&");
        for (String queryParam : queryParams) {
            String[] keyValue = queryParam.split("=");
            if (keyValue.length > 2 || keyValue.length < 1)
                throw halt(BAD_REQUEST, "Bad query string");
            if (keyValue.length > 1) {
                String value = keyValue[1];
                try {
                    value = URLDecoder.decode(value, "utf-8");
                }
                catch (UnsupportedEncodingException e) {
                    Logger.exception(e);
                    throw halt(SERVER_ERROR, "Failed to decode params");
                }
                if (!ids.contains(value) && keyValue[0].equals("id"))
                    ids.add(value);
                else if (!names.contains(value) && keyValue[0].equals("name"))
                    names.add(value);
            }
        }

        // Check cache
        ArrayList<String> req = new ArrayList<>();
        req.addAll(ids);
        req.addAll(names);
        String requestId = ApiCache.createKey("helix/games", req.toArray());
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Fetch live data
        List<Game> games = getGames(ids, names);
        String json = gson.toJson(games);
        cache.set(requestId, json);
        return json;
    }
}
