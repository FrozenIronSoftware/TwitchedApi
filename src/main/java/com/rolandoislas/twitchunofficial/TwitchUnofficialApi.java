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
import com.rolandoislas.twitchunofficial.util.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Pagination;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserList;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
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
        String username = request.queryParams("username");
        if (username == null || username.isEmpty())
            throw halt(BAD_REQUEST, "Missing username query parameter");
        // Check cache
        String requestId = ApiCache.createKey("hls", username);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data
        try {
            Process streamlink = new ProcessBuilder(
                    "streamlink",
                    "--json",
                    String.format("twitch.tv/%s", username)).start();
            streamlink.waitFor();
            Scanner scanner = new Scanner(streamlink.getInputStream()).useDelimiter("\\A");
            if (scanner.hasNext()) {
                String json = scanner.next();
                cache.set(requestId, json);
                return json;
            }
        } catch (IOException | InterruptedException e) {
            Logger.warn("Failed to call streamlink");
            Logger.exception(e);
        }
        throw halt(SERVER_ERROR, "Failed to fetch data");
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
        String requestUrl = String.format("%s/games/top?limit=%s&offset=%s", Endpoints.API.getURL(), limit, offset);
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
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
            stream.setUserName(userName == null ? "" : userName);
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
            List<User> users = getUsers(ids);
            if (users == null)
                throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
            // Store missing ids
            for (User user : users)
                nameIdMap.put(user.getId(), user.getDisplayName());
            cache.setUserNames(nameIdMap);
        }
        else if (type.equals(Id.GAME)) {
            List<Game> games = getGames(ids);
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
    private static List<Game> getGames(List<String> ids) {
        if (ids.isEmpty())
            throw halt(BAD_REQUEST, "Bad request: missing game id");
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
        for (String id : ids)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("id", id));
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
    private static List<User> getUsers(List<String> userIds) {
        if (userIds.isEmpty())
            throw halt(BAD_REQUEST, "Bad request: missing user id");
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
        for (String id : userIds)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("id", id));
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
}
