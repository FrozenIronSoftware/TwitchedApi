/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.Id;
import com.rolandoislas.twitchunofficial.data.Playlist;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.twitch.Token;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.util.twitch.helix.FollowList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameViewComparator;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Pagination;
import com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserName;
import com.rolandoislas.twitchunofficial.util.twitch.kraken.AppToken;
import com.rolandoislas.twitchunofficial.util.twitch.kraken.Video;
import me.philippheuer.twitch4j.TwitchClient;
import me.philippheuer.twitch4j.TwitchClientBuilder;
import me.philippheuer.twitch4j.auth.model.OAuthCredential;
import me.philippheuer.twitch4j.enums.Endpoints;
import me.philippheuer.twitch4j.exceptions.RestException;
import me.philippheuer.twitch4j.model.Channel;
import me.philippheuer.twitch4j.model.Community;
import me.philippheuer.twitch4j.model.CommunityList;
import me.philippheuer.twitch4j.model.Stream;
import me.philippheuer.twitch4j.model.TopGame;
import me.philippheuer.twitch4j.model.TopGameList;
import me.philippheuer.util.rest.HeaderRequestInterceptor;
import me.philippheuer.util.rest.QueryRequestInterceptor;
import me.philippheuer.util.rest.RestErrorHandler;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

public class TwitchUnofficialApi {
    private static final Pattern DURATION_REGEX = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)");
    static final int BAD_REQUEST = 400;
    static final int SERVER_ERROR =  500;
    static final int BAD_GATEWAY = 502;
    private static final String API = "https://api.twitch.tv/helix";
    private static final String API_RAW = "https://api.twitch.tv/api";
    private static final String API_USHER = "https://usher.ttvnw.net";
    static TwitchClient twitch;
    static Gson gson;
    private static OAuthCredential twitchOauth;

    /**
     * Send a JSON error message to the current requester
     * @param code HTTP status code
     * @param message error message
     * @return halt
     */
    @NotCached
    static HaltException halt(int code, String message) {
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
    static void checkAuth(Request request) {
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
        if (request.splat().length < 1)
            return null;
        int fps = 60;
        String fileName = request.splat()[0];
        if (request.splat().length >= 2) {
            fps = (int) parseLong(request.splat()[0]);
            fileName = request.splat()[1];
        }
        String[] split = fileName.split("\\.");
        if (split.length < 2 || !split[1].equals("m3u8"))
            return null;
        String username = split[0];
        // Check cache
        String requestId = ApiCache.createKey("hls", username);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data

        // Construct template
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();

        // Request channel token
        Token token = getVideoAccessToken(Token.TYPE.CHANNEL, username);

        // Request HLS playlist
        String hlsPlaylistUrl = String.format(API_USHER + "/api/channel/hls/%s.m3u8", username);
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Accept", "*/*"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("player", "twitchunofficialroku"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("token", token.getToken()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("sig", token.getSig()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("p",
                String.valueOf((int)(Math.random() * Integer.MAX_VALUE))));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", "any"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("$allow_audio_only", "false"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("$allow_source", "false"));
        ResponseEntity<String> playlist = restTemplate.exchange(hlsPlaylistUrl, HttpMethod.GET, null,
                String.class);

        // Parse playlist
        String playlistString = playlist.getBody();
        if (playlistString == null)
            return null;
        playlistString = cleanMasterPlaylist(playlistString, fps < 60);
        // Cache and return
        cache.set(requestId, playlistString);
        response.type("audio/mpegurl");
        return playlistString;
    }

    /**
     * Get an access token for a channel stream or a VOD
     * @param type type of stream to get
     * @return token
     */
    @NotNull
    private static Token getVideoAccessToken(Token.TYPE type, String id) {
        String url;
        switch (type) {
            case CHANNEL:
                url = "/channels/%s/access_token";
                break;
            case VOD:
                url = "/vods/%s/access_token";
                break;
            default:
                throw new RuntimeException("Invalid type specified");
        }
        String hlsTokenUrl = String.format(API_RAW + url, id);
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        ResponseEntity<String> tokenResponse;
        try {
            tokenResponse = restTemplate.exchange(hlsTokenUrl, HttpMethod.GET, null,
                    String.class);
        }
        catch (RestException e) {
            throw halt(404, "Not found");
        }
        Token token;
        try {
            token = gson.fromJson(tokenResponse.getBody(), Token.class);
            if (token.getToken() == null || token.getSig() == null)
                throw halt(SERVER_ERROR, "Invalid data: Twitch API may have changed");
        }
        catch (JsonSyntaxException e) {
            throw halt(BAD_GATEWAY, "Failed to parse token data.");
        }
        return token;
    }

    /**
     * Return a master playlist for a VOD
     * @param request request
     * @param response response
     * @return m3u8
     */
    public static String getVodData(Request request, Response response) {
        checkAuth(request);
        if (request.splat().length < 1)
            return null;
        int fps = 60;
        String fileName = request.splat()[0];
        if (request.splat().length >= 2) {
            fps = (int) parseLong(request.splat()[0]);
            fileName = request.splat()[1];
        }
        String[] split = fileName.split("\\.");
        if (split.length < 2 || !split[1].equals("m3u8"))
            return null;
        String vodId = split[0];
        // Check cache
        String requestId = ApiCache.createKey("vod", vodId);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Fetch live data
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        // Request VOD token
        Token token = getVideoAccessToken(Token.TYPE.VOD, vodId);

        // Request HLS playlist
        String hlsPlaylistUrl = String.format(API_USHER + "/vod/%s.m3u8", vodId);
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Accept", "*/*"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("nauth", token.getToken()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("nauthsig", token.getSig()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("p",
                String.valueOf((int)(Math.random() * Integer.MAX_VALUE))));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", "any"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("$allow_audio_only", "false"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("$allow_source", "false"));
        ResponseEntity<String> playlist = restTemplate.exchange(hlsPlaylistUrl, HttpMethod.GET, null,
                String.class);

        // Parse playlist
        String playlistString = playlist.getBody();
        if (playlistString == null)
            return null;
        playlistString = cleanMasterPlaylist(playlistString, fps < 60);
        // Cache and return
        cache.set(requestId, playlistString);
        response.type("audio/mpegurl");
        return playlistString;
    }

    /**
     * Parse a master playlist file, removing entries based on parameters
     *
     * @param playlistString
     * @param limitFps if true only 30 FPS entries and/or source are left
     *                 if 60fps streams are the only option, they will be added regardless
     * @return clean master playlist
     */
    private static String cleanMasterPlaylist(String playlistString, boolean limitFps) {
        // Parse lines
        List<String> playlist = new ArrayList<>();
        List<Playlist> playlists = new ArrayList<>();
        String[] playlistSplit = playlistString.split("\r?\n");
        for (int lineIndex = 0; lineIndex < playlistSplit.length; lineIndex++) {
            String line = playlistSplit[lineIndex];
            // Not media line
            if (!line.startsWith("#EXT-X-MEDIA")) {
                playlist.add(line);
            }
            // Media line
            else {
                // EOF - add line but do not force add others
                if (lineIndex + 2 >= playlistSplit.length) {
                    playlist.add(line);
                }
                // Add line and two after it to playlists list
                else {
                    Playlist stream = new Playlist(line, playlistSplit[lineIndex + 1], playlistSplit[lineIndex + 2]);
                    playlists.add(stream);
                    lineIndex += 2;
                }
            }

        }
        // Add compatible playlists
        boolean addedPlaylist = false;
        for (Playlist stream : playlists) {
            if (!limitFps || stream.getFps() <= 30) {
                playlist.addAll(stream.getLines());
                addedPlaylist = true;
            }
        }
        if (!addedPlaylist)
            return playlistString;
        // Return playlist
        StringBuilder cleanedPlaylist = new StringBuilder();
        for (String line : playlist)
            cleanedPlaylist.append(line).append("\r\n");
        return cleanedPlaylist.toString();
    }

    /**
     * Initialize the Twitch API wrapper
     * @param twitchClientId client id
     * @param twitchClientSecret secret
     */
    @NotCached
    static void init(String twitchClientId, String twitchClientSecret) {
        TwitchUnofficialApi.gson = new Gson();
        TwitchUnofficialApi.twitch = TwitchClientBuilder.init()
                .withClientId(twitchClientId)
                .withClientSecret(twitchClientSecret)
                .withCredential(getAppToken(twitchClientId, twitchClientSecret))
                .build();
        // Set credential
        for (Map.Entry<String, OAuthCredential>credentialEntry :
                twitch.getCredentialManager().getOAuthCredentials().entrySet()) {
            twitchOauth = credentialEntry.getValue();
        }
        if (twitchOauth == null)
            Logger.warn("No Oauth token provided. Requests will be rate limited to 30 per minute.");
    }

    /**
     * Request an app token
     * @return app token
     * @param twitchClientId client id
     * @param twitchClientSecret secret
     */
    @Nullable
    private static String getAppToken(String twitchClientId, String twitchClientSecret) {
        // Construct template
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(new ArrayList<>());
        restTemplate.setErrorHandler(new RestErrorHandler());

        // Request app token
        String appTokenUrl = Endpoints.API.getURL() + "/oauth2/token";

        restTemplate.getInterceptors().add(new QueryRequestInterceptor("client_id", twitchClientId));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("client_secret", twitchClientSecret));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("grant_type", "client_credentials"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("scope", ""));

        ResponseEntity<String> tokenResponse = null;
        try {
            tokenResponse = restTemplate.exchange(appTokenUrl, HttpMethod.POST, null,
                    String.class);
        }
        catch (RestException e) {
            Logger.warn(StringUtils.repeat("=", 80));
            Logger.warn("Failed to get Twitch app token!");
            Logger.warn(e.getRestError().getError());
            Logger.warn(e.getRestError().getMessage());
            Logger.warn(StringUtils.repeat("=", 80));
            Logger.exception(e);
            return null;
        }
        AppToken token;
        try {
            token = gson.fromJson(tokenResponse.getBody(), AppToken.class);
            if (token.getAccessToken() == null) {
                Logger.warn(StringUtils.repeat("=", 80));
                Logger.warn("Failed to get Twitch app token!");
                Logger.warn(StringUtils.repeat("=", 80));
                return null;
            }
            Logger.info("Access token expires in: %d seconds.", token.getExpiresIn());
            return token.getAccessToken();
        }
        catch (JsonSyntaxException e) {
            Logger.warn(StringUtils.repeat("=", 80));
            Logger.warn("Failed to get Twitch app token!");
            Logger.warn(StringUtils.repeat("=", 80));
            return null;
        }
    }

    /**
     * Get a list of games
     * @param request request
     * @param response response
     * @return games json
     */
    @Cached
    static String getTopGamesKraken(Request request, Response response) {
        checkAuth(request);
        // Parse parameters
        String limit = request.queryParamOrDefault("limit", "10");
        String offset = request.queryParamOrDefault("offset", "0");
        // Check cache
        String requestId = ApiCache.createKey("kraken/games/top", limit, offset);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Request
        @Nullable List<TopGame> games = getTopGamesKraken(limit, offset);

        if (games == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");

        // Store and return
        String json = gson.toJson(games);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Request top games from the Kraken end point
     * @param limit limit
     * @param offset offset
     * @return top games
     */
    @Nullable
    @NotCached
    private static List<TopGame> getTopGamesKraken(@Nullable String limit, @Nullable String offset) {
        // Fetch live data
        String requestUrl = String.format("%s/games/top", Endpoints.API.getURL());
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        if (limit != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("limit", limit));
        if (offset != null)
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
        return games;
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
        String first = request.queryParams("first");
        String game = request.queryParams("game_id");
        String language = request.queryParams("language");
        String streamType = request.queryParamOrDefault("type", "all");
        String userId = request.queryParams("user_id");
        String userLogin = request.queryParams("user_login");
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParamOrDefault("limit", "20");
        // Set after based on offset
        String afterFromOffset = getAfterFromOffset(offset, first);
        if (afterFromOffset != null)
            after = afterFromOffset;
        // Check cache
        String requestId = ApiCache.createKey("helix/streams", after, before, community, first, game, language,
                streamType, userId, userLogin, offset);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Request live
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = getStreams(
                after,
                before,
                Collections.singletonList(community),
                first,
                Collections.singletonList(game),
                Collections.singletonList(language),
                streamType,
                Collections.singletonList(userId),
                Collections.singletonList(userLogin)
        );

        // Cache and return
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get streams from the helix end point
     * @param after cursor
     * @param before cursor
     * @param communities community ids
     * @param first limit
     * @param games game ids
     * @param languages lang ids
     * @param streamType stream type
     * @param userIdsParam user ids
     * @param userLoginsParam user logins
     * @return streams
     */
    @NotNull
    @NotCached
    private static List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> getStreams(
            @Nullable String after,
            @Nullable String before,
            @Nullable List<String> communities,
            @Nullable String first,
            @Nullable List<String> games,
            @Nullable List<String> languages,
            @Nullable String streamType,
            @Nullable List<String> userIdsParam,
            @Nullable List<String> userLoginsParam) {
        // Request live

        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = null;
        // Endpoint
        String requestUrl = String.format("%s/streams", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Parameters
        if (after != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("after", after));
        if (before != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("before", before));
        if (communities != null)
            for (String community : communities)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("community_id", community));
        if (first != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("first", first));
        if (games != null)
            for (String game : games)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("game_id", game));
        if (languages != null)
            for (String language : languages)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("language", language));
        if (streamType != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", streamType));
        if (userIdsParam != null)
            for (String userId : userIdsParam)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("user_id", userId));
        if (userLoginsParam != null)
            for (String userLogin : userLoginsParam)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("user_login", userLogin));
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null,
                    String.class);
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
        addNamesToStreams(streams);

        return streams;
    }

    /**
     * Add user names and game names to a list of streams
     * @param streams stream list
     */
    private static void addNamesToStreams(List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream>
                                                      streams) {
        List<String> gameIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streams) {
            userIds.add(stream.getUserId());
            gameIds.add(stream.getGameId());
        }
        Map<String, String> userNames;
        try {
            userNames = getUserNames(userIds);
        }
        catch (HaltException | RestException e) {
            userNames = new HashMap<>();
        }
        Map<String, String> gameNames;
        try {
            gameNames = getGameNames(gameIds);
        }
        catch (HaltException | RestException e) {
            gameNames = new HashMap<>();
        }
        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streams) {
            String userName = userNames.get(stream.getUserId());
            try {
                stream.setUserName(userName == null || userName.isEmpty() ?
                        new UserName() : gson.fromJson(userName, UserName.class));
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                stream.setUserName(new UserName());
            }
            String gameName = gameNames.get(stream.getGameId());
            stream.setGameName(gameName == null ? "" : gameName);
        }
    }

    /**
     * Get a rest templates with the oauth token added as a bearer token
     * @param oauth token to add to header
     * @return rest templates
     */
    private static RestTemplate getPrivilegedRestTemplate(OAuthCredential oauth) {
        RestTemplate restTemplate = twitch.getRestClient().getPlainRestTemplate();
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Authorization",
                String.format("Bearer %s", oauth.getToken())));
        return restTemplate;
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
            List<User> users = getUsers(ids, null, null);
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
    @Nullable
    private static List<Game> getGames(@Nullable List<String> ids, @Nullable List<String> names) {
        if ((ids == null || ids.isEmpty()) && (names == null || names.isEmpty()))
            throw halt(BAD_REQUEST, "Bad request: missing game id or name");
        // Request live
        List<Game> games = null;
        // Endpoint
        String requestUrl = String.format("%s/games", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
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
     * @param userNames name to poll
     * @param token oauth token to use instead of names or ids. if names or ids are not null, the token is ignored
     * @return list of users
     */
    @NotCached
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token) {
        if ((userIds == null || userIds.isEmpty()) && (userNames == null || userNames.isEmpty()) && token == null)
            throw halt(BAD_REQUEST, "Bad request: missing user id or user name");
        // Request live
        List<User> users = null;
        // Endpoint
        String requestUrl = String.format("%s/users", API);
        RestTemplate restTemplate;
        if (token != null) {
            OAuthCredential oauth = new OAuthCredential(token);
            restTemplate = getPrivilegedRestTemplate(oauth);
        }
        else if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
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
        checkAuth(request);
        // Parse query params
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String[] queryParams = request.queryString() != null ? request.queryString().split("&") : new String[0];
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

    /**
     * Get streams that are online and that a user follows
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    static String getUserFollowedStreamsHelix(Request request, Response response) {
        checkAuth(request);
        // Params
        String offset = request.queryParamOrDefault("offset", "0");
        String limit = request.queryParamOrDefault("limit", "20");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            throw halt(BAD_REQUEST, "Empty token");
        // Check cache
        String requestId = ApiCache.createKey("helix/user/follows/streams", offset, limit, token);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Calculate the from id from the token
        List<User> fromUsers = getUsers(null, null, token);
        if (fromUsers.size() == 0)
            throw halt(BAD_REQUEST, "User token invalid");
        String fromId = fromUsers.get(0).getId();
        // Get follows
        // FIXME all follows will need to be polled in order to show accurate live channels
        FollowList userFollows = getUserFollows(getAfterFromOffset(offset, limit), null, limit,
                fromId, null);
        if (userFollows == null || userFollows.getFollows() == null)
            throw halt(SERVER_ERROR, "Failed to connect to Twitch API");
        List<String> followIds = new ArrayList<>();
        for (Follow follow : userFollows.getFollows())
            if (follow.getToId() != null)
                followIds.add(follow.getToId());
        @NotNull List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams =
                getStreams(getAfterFromOffset("0", limit), null, null, limit,
                        null, null, null, followIds, null);
        // Cache and return
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Gets user follows
     * @param after after cursor
     * @param before before cursor
     * @param first limit
     * @param fromId user id
     * @param toId user id
     * @return follow list
     */
    @NotCached
    @Nullable
    private static FollowList getUserFollows(@Nullable String after, @Nullable String before, @Nullable String first,
                                             @Nullable String fromId, @Nullable String toId) {
       if ((fromId == null || fromId.isEmpty()) && (toId == null || toId.isEmpty()))
           throw halt(BAD_REQUEST, "Missing to or from id");
        // Request live

        // Endpoint
        String requestUrl = String.format("%s/users/follows", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Parameters
        if (after != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("after", after));
        if (before != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("before", before));
        if (first != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("first", first));
        if (fromId != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("from_id", fromId));
        if (toId != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("to_id", toId));
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null,
                    String.class);
            try {
                return gson.fromJson(responseObject.getBody(), FollowList.class);
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException)
                Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            else
                Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        return null;
    }

    /**
     * Get the Twitch after cursor value from the offset
     * @param offset page offset stating at zero
     * @param first page item limit
     * @return cursor(after)
     */
    @Nullable
    @NotCached
    private static String getAfterFromOffset(@Nullable String offset, String first) {
        if (offset != null) {
            try {
                long offsetLong = Long.parseLong(offset);
                long firstLong = Long.parseLong(first);
                Pagination pagination = new Pagination(
                        (offsetLong - 1) * firstLong,
                        (offsetLong + 1) * firstLong
                );
                return pagination.getCursor();
            }
            catch (NumberFormatException ignore) {}
        }
        return null;
    }

    /**
     * Get users that follow a user or users that a user follows...yeah
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    static String getUserFollowHelix(Request request, Response response) {
        checkAuth(request);
        // Params
        String after = request.queryParams("after");
        String before = request.queryParams("before");
        String first = request.queryParams("first");
        String fromId = request.queryParams("from_id");
        String toId = request.queryParams("to_id");
        String fromLogin = request.queryParams("from_login");
        String toLogin = request.queryParams("to_login");
        String noCache = request.queryParams("no_cache");
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParamOrDefault("limit", "20");
        // Set after based on offset
        String afterFromOffset = getAfterFromOffset(offset, first);
        if (afterFromOffset != null)
            after = afterFromOffset;
        // Convert logins to ids
        if (fromLogin != null) {
            List<User> users = getUsers(null, Collections.singletonList(fromLogin), null);
            if (users.size() == 1)
                fromId = users.get(0).getId();
        }
        if (toLogin != null) {
            List<User> users = getUsers(null, Collections.singletonList(toLogin), null);
            if (users.size() == 1)
                toId = users.get(0).getId();
        }
        // Check cache
        String requestId = ApiCache.createKey("helix/user/follows", after, before, first, fromId, toId);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null && (noCache == null || !noCache.equals("true")))
            return cachedResponse;
        // Get data
        FollowList followList = getUserFollows(after, before, first, fromId, toId);
        if (followList == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Cache and return
        String json = gson.toJson(followList.getFollows());
        if (noCache == null || !noCache.equals("true"))
            cache.set(requestId, json);
        return json;
    }

    /**
     * Search on the kraken endpoint
     * This combines games, channels, and streams search endpoints
     * Data returned is expected to be in "KRAKEN" game or community format json
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    static String getSearchKraken(Request request, Response response) {
        checkAuth(request);
        // All
        @Nullable String query = request.queryParams("query");
        String type = request.queryParamOrDefault("type", "streams");
        String limit = request.queryParamOrDefault("limit", "20");
        String offset = request.queryParamOrDefault("offset", "0");
        String hls = request.queryParamOrDefault("hls", "true");
        String live = request.queryParamOrDefault("live", "false");
        // Check params
        if (query == null || query.isEmpty())
            throw halt(BAD_REQUEST, "Empty query");
        long limitLong;
        try {
            limitLong = Long.parseLong(limit);
        }
        catch (NumberFormatException e) {
            throw halt(BAD_REQUEST, "Invalid limit");
        }
        // Check cache
        String requestId = ApiCache.createKey("kraken/search", query, type, limit, offset, hls, live);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data
        String json;
        // Used by switch case
        ArrayList<String> userIds = new ArrayList<>();
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streamsHelix = new ArrayList<>();
        List<Game> games = new ArrayList<>();
        switch (type) {
            case "streams":
                // HLS param is ignored
                List<Stream> streams = twitch.getSearchEndpoint().getStreams(query, Optional.of(limitLong));
                if (streams == null)
                    throw halt(BAD_GATEWAY, "Failed to get streams");
                for (Stream stream : streams)
                    userIds.add(String.valueOf(stream.getChannel().getId()));
                if (userIds.size() > 0)
                    streamsHelix = getStreams(
                            null,
                            null,
                            null,
                            "100",
                            null,
                            null,
                            null,
                            userIds,
                            null
                    );
                json = gson.toJson(streamsHelix);
                break;
            case "channels":
                // Get channels from search
                List<Channel> channels = twitch.getSearchEndpoint().getChannels(query, Optional.of(limitLong));
                if (channels == null)
                    throw halt(BAD_GATEWAY, "Failed to get channels");
                // Get games
                List<String> gameNames = new ArrayList<>();
                for (Channel channel : channels)
                    gameNames.add(String.valueOf(channel.getGame()));
                games = getGames(null, gameNames);
                if (games == null)
                    throw halt(BAD_GATEWAY, "Failed to get games");
                // Get streams
                for (Channel channel : channels)
                    userIds.add(String.valueOf(channel.getId()));
                streamsHelix = getStreams(
                        null,
                        null,
                        null,
                        "100",
                        null,
                        null,
                        null,
                        userIds,
                        null
                );
                // Populate Streams
                SimpleDateFormat krakenDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");
                // ISO8601
                SimpleDateFormat hexlixDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                channelToStream:
                for (Channel channel : channels) {
                    // Check if stream is live
                    for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streamsHelix)
                        if (String.valueOf(stream.getUserId()).equals(String.valueOf(channel.getId())))
                            continue channelToStream;
                    // Add stream from channel data
                    com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream =
                            new com.rolandoislas.twitchunofficial.util.twitch.helix.Stream();
                    stream.setId("null"); // No stream id
                    stream.setUserId(String.valueOf(channel.getId()));
                    for (Game game : games)
                        if (game.getName() != null && game.getName().equalsIgnoreCase(channel.getGame()))
                            stream.setGameId(String.valueOf(game.getId()));
                    if (stream.getGameId() == null)
                        stream.setGameId("null");
                    stream.setCommunityIds(new ArrayList<>()); // No community ids
                    stream.setType("user"); // Set the type to user (This is not a valid Twitch API value)
                    stream.setTitle(String.valueOf(channel.getStatus()));
                    stream.setViewerCount(channel.getViews()); // No viewer count
                    // Converts the time string to ISO8601
                    String createdAt = "";
                    try {
                        Date krakenDate = krakenDateFormat.parse(String.valueOf(channel.getCreatedAt()));
                        createdAt =  hexlixDateFormat.format(krakenDate);
                    }
                    catch (ParseException e) {
                        e.printStackTrace();
                    }
                    stream.setStartedAt(createdAt);
                    stream.setLanguage(String.valueOf(channel.getLanguage()));
                    // This replaces Helix thumbnail size values, but the channel data has Kraken thumbnails.
                    // Helix data can be polled, but it would be another API call.
                    // At the moment the thumbnails are full size.
                    stream.setThumbnailUrl(String.valueOf(channel.getVideoBanner())
                            .replaceAll("\\d+x\\d+", "{width}x{height}"));
                    stream.setUserName(new UserName(String.valueOf(channel.getName()),
                            String.valueOf(channel.getDisplayName())));
                    stream.setGameName(String.valueOf(channel.getGame()));
                    streamsHelix.add(stream);
                }
                json = gson.toJson(streamsHelix);
                break;
            case "games":
                // Search
                List<me.philippheuer.twitch4j.model.Game> gamesKraken =
                        twitch.getSearchEndpoint().getGames(query, Optional.of(live.equals("true")));
                // Get games from the Helix endpoint
                List<String> gameIds = new ArrayList<>();
                if (gamesKraken != null)
                    for (me.philippheuer.twitch4j.model.Game game : gamesKraken)
                        gameIds.add(String.valueOf(game.getId()));
                if (gameIds.size() > 0)
                    games = getGames(gameIds, null);
                // Add viewers to helix data
                if (games != null && gamesKraken != null) {
                    for (Game game : games)
                        for (me.philippheuer.twitch4j.model.Game gameKraken : gamesKraken)
                            if (String.valueOf(game.getId()).equals(String.valueOf(gameKraken.getId())))
                                game.setViewers(gameKraken.getPopularity());
                    games.sort(new GameViewComparator().reversed());
                }
                json = gson.toJson(games);
                break;
            default:
                throw halt(BAD_REQUEST, "Invalid type");
        }
        // Cache and return
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get top games from the Helix end point
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    static String getTopGamesHelix(Request request, Response response) {
        checkAuth(request);
        // Params
        String after = request.queryParams("after");
        String before = request.queryParams("before");
        String first = request.queryParams("first");
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParamOrDefault("limit", "20");
        // Set after based on offset
        String afterFromOffset = getAfterFromOffset(offset, first);
        if (afterFromOffset != null)
            after = afterFromOffset;
        // Check cache
        String requestId = ApiCache.createKey("helix/games/top", after, before, first);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Fetch live data
        String requestUrl = String.format("%s/games/top", API);
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        if (after != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("after", after));
        if (before != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("before", before));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("first", first));
        // REST Request
        List<Game> games = null;
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null,
                    String.class);
            try {
                GameList gameList = gson.fromJson(responseObject.getBody(), GameList.class);
                games = gameList.getGames();
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (RestClientException | RestException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (games == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Add viewer info
        List<TopGame> gamesKraken = getTopGamesKraken("100", "0");
        if (gamesKraken != null) {
            for (Game game : games)
                for (TopGame gameKraken : gamesKraken)
                    if (String.valueOf(game.getId()).equals(
                            String.valueOf(gameKraken.getGame() != null ? gameKraken.getGame().getId() : null)))
                        game.setViewers(gameKraken.getViewers());
        }
        // Store and return
        String json = gson.toJson(games);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Request user info from the Helix endpoint
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    public static String getUsersHelix(Request request, Response response) {
        checkAuth(request);
        // Params
        String token = AuthUtil.extractTwitchToken(request);
        List<String> ids = new ArrayList<>();
        List<String> logins = new ArrayList<>();
        for (Map.Entry<String, String[]> param : request.queryMap().toMap().entrySet()) {
            if (param.getKey().equals("id"))
                ids.addAll(Arrays.asList(param.getValue()));
            else if (param.getKey().equals("login"))
                logins.addAll(Arrays.asList(param.getValue()));
        }
        if (token == null && ids.isEmpty() && logins.isEmpty())
            throw halt(BAD_REQUEST, "Missing token/id/login");
        // Check cache
        Object[] keyParams = new Object[ids.size() + logins.size() + 1];
        keyParams[0] = token;
        System.arraycopy(ids.toArray(), 0, keyParams, 1, ids.size());
        System.arraycopy(logins.toArray(), 0, keyParams, ids.size() + 1, logins.size());
        String requestId = ApiCache.createKey("helix/users", keyParams);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live
        List<User> users = getUsers(ids, logins, token);
        if (users == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Cache and return
        String json = gson.toJson(users);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Request videos (VODs) from the helix endpoint
     * @param request request
     * @param response response
     * @return video json
     */
    @Cached
    public static String getVideosHelix(Request request, Response response) {
        checkAuth(request);
        // Parse query params
        String userId = request.queryParams("user_id");
        String gameId = request.queryParams("game_id");
        String after = request.queryParams("after");
        String before = request.queryParams("before");
        String first = request.queryParams("first");
        String language = request.queryParams("language");
        String period = request.queryParams("period");
        String sort = request.queryParams("sort");
        String type = request.queryParams("type");
        List<String> ids = new ArrayList<>();
        String[] queryParams = request.queryString() != null ? request.queryString().split("&") : new String[0];
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
            }
        }
        // Additional params
        if (first == null || first.isEmpty())
            first = request.queryParams("limit");
        // Check params
        if ((userId == null || userId.isEmpty()) && (gameId == null || gameId.isEmpty()) && ids.isEmpty())
            throw halt(BAD_REQUEST, "Missing user_id, game_id, or id");
        // Check cache
        List<String> params = new ArrayList<>(ids);
        params.add(userId);
        params.add(gameId);
        params.add(after);
        params.add(before);
        params.add(first);
        params.add(language);
        params.add(period);
        params.add(sort);
        params.add(type);
        String requestId = ApiCache.createKey("helix/videos", params.toArray());
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live data
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> videos = getVideos(ids, userId, gameId, after,
                before, first, language, period, sort, type);
        if (videos == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Add game to video from Kraken endpoint
        // Only do this for a single video
        if (ids.size() == 1 && videos.size() == 1) {
            @Nullable Video video = getVideoKraken(ids.get(0));
            @Nullable List<Game> games = null;
            if (video != null && video.getGame() != null && !video.getGame().isEmpty())
                games = getGames(null, new ArrayList<>(Collections.singleton(video.getGame())));
            if (games != null && games.size() == 1) {
                videos.get(0).setGameId(games.get(0).getId());
                videos.get(0).setGameName(games.get(0).getName());
            }
        }
        // Cache and return
        String json = gson.toJson(videos);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Get a single video by ID from the Kraken endpoint
     * @param id video id
     * @return video
     */
    @Nullable
    @NotCached
    private static Video getVideoKraken(String id) {
        // Endpoint
        String requestUrl = String.format("%s/videos/%s", Endpoints.API.getURL(), id);
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        // REST Request
        try {
            Logger.verbose("Rest Request to [%s]", requestUrl);
            return restTemplate.getForObject(requestUrl, Video.class);
        }
        catch (RestException e) {
            Logger.extra("RestException: " + e.getRestError().toString());
            Logger.exception(e);
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        return null;
    }

    /**
     * Request videos from the Helix API
     * @param ids video ids
     * @param userId user id
     * @param gameId game id
     * @param after cursor
     * @param before cursor
     * @param first limit per page
     * @param language language
     * @param period time period - all, day, month, week
     * @param sort sort method - time, trending, views
     * @param type type - all, upload, archive, highlight
     * @return list of videos
     */
    @NotCached
    @Nullable
    private static List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> getVideos(
            @Nullable List<String> ids,
            @Nullable String userId,
            @Nullable String gameId,
            @Nullable String after,
            @Nullable String before,
            @Nullable String first,
            @Nullable String language,
            @Nullable String period,
            @Nullable String sort,
            @Nullable String type) {
        // Rest template
        RestTemplate restTemplate;
        if (twitchOauth != null)
            restTemplate = getPrivilegedRestTemplate(twitchOauth);
        else
            restTemplate = twitch.getRestClient().getRestTemplate();
        // Rest URL
        String requestUrl = String.format("%s/videos", API);
        if (ids != null)
            for (String id : ids)
                restTemplate.getInterceptors().add(new QueryRequestInterceptor("id", id));
        if (userId != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("user_id", userId));
        if (gameId != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("game_id", gameId));
        if (after != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("after", after));
        if (before != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("before", before));
        if (first != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("first", first));
        if (language != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("language", language));
        if (period != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("period", period));
        if (sort != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("sort", sort));
        if (type != null)
            restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", type));
        // REST Request
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> videos = null;
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            ResponseEntity<String> responseObject = restTemplate.exchange(requestUrl, HttpMethod.GET, null,
                    String.class);
            try {
                StreamList streamList = gson.fromJson(responseObject.getBody(), StreamList.class);
                videos = streamList.getStreams();
                // Convert the duration string to seconds
                for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream video : videos) {
                    if (video.getDuration() == null)
                        continue;
                    Matcher matcher = DURATION_REGEX.matcher(video.getDuration());
                    if (!matcher.matches())
                        continue;
                    long durationSeconds = 0;
                    // Seconds
                    if (matcher.groupCount() >= 4)
                        durationSeconds += parseLong(matcher.group(4));
                    // Minutes
                    if (matcher.groupCount() >= 3)
                        durationSeconds += parseLong(matcher.group(3)) * 60;
                    // Hours
                    if (matcher.groupCount() >= 2)
                        durationSeconds += parseLong(matcher.group(2)) * 60 * 60;
                    // Days
                    if (matcher.groupCount() >= 1)
                        durationSeconds += parseLong(matcher.group(1)) * 24 * 60 * 60;
                    video.setDurationSeconds(durationSeconds);
                }
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (RestClientException | RestException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (videos != null)
            addNamesToStreams(videos);
        return videos;
    }

    /**
     * Safely parse an long
     * @param number string to parse
     * @return parsed long or 0 on failure
     */
    private static long parseLong(@Nullable String number) {
        long parsedLong = 0;
        try {
            if (number != null)
                parsedLong = Long.parseLong(number);
        }
        catch (NumberFormatException ignore) {}
        return parsedLong;
    }

    /**
     * Follow a channel
     * @param request request
     * @param response response
     * @return empty html
     */
    @NotCached
    public static String followKraken(Request request, Response response) {
        checkAuth(request);
        // Params
        String id = request.queryParams("id");
        long followIdLong = parseLong(id);
        if (followIdLong == 0)
            throw halt(BAD_REQUEST, "No id");
        @Nullable String token = AuthUtil.extractTwitchToken(request);
        if (token == null)
            throw halt(BAD_REQUEST, "No token");
        // Follow
        OAuthCredential oauth = new OAuthCredential(token);
        List<User> users = getUsers(null, null, token);
        if (users.size() != 1)
            throw halt(BAD_REQUEST, "Invalid token");
        oauth.setUserId(parseLong(users.get(0).getId()));

        // Endpoint
        String requestUrl = String.format("%s/users/%s/follows/channels/%s", Endpoints.API.getURL(), oauth.getUserId(),
                followIdLong);
        RestTemplate restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(oauth);

        // REST Request
        try {
            Logger.verbose("Rest Request to [%s]", requestUrl);
            restTemplate.put(requestUrl, "");
        }
        catch (RestException e) {
            Logger.extra("RestException: " + e.getRestError().toString());
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        return "{}";
    }

    /**
     * Unfollow channel
     * @param request request
     * @param response response
     * @return empty html
     */
    @NotCached
    public static String unfollowKraken(Request request, Response response) {
        checkAuth(request);
        // Params
        String id = request.queryParams("id");
        long followIdLong = parseLong(id);
        if (followIdLong == 0)
            throw halt(BAD_REQUEST, "No id");
        @Nullable String token = AuthUtil.extractTwitchToken(request);
        if (token == null)
            throw halt(BAD_REQUEST, "No token");
        // Unfollow
        OAuthCredential oauth = new OAuthCredential(token);
        List<User> users = getUsers(null, null, token);
        if (users.size() != 1)
            throw halt(BAD_REQUEST, "Invalid token");
        oauth.setUserId(parseLong(users.get(0).getId()));
        twitch.getUserEndpoint().unfollowChannel(oauth, followIdLong);
        return "{}";
    }

    /**
     * Get user follows as streams from the kraken endpoint
     * @param request request
     * @param response response
     * @return json
     */
    public static String getUserFollowedStreamsKraken(Request request, Response response) {
        checkAuth(request);
        // Params
        String offset = request.queryParamOrDefault("offset", "0");
        String limit = request.queryParamOrDefault("limit", "20");
        String direction = request.queryParamOrDefault("direction", "desc");
        String sortBy = request.queryParamOrDefault("sortby", "last_broadcast");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            throw halt(BAD_REQUEST, "Empty token");
        // Check cache
        String requestId = ApiCache.createKey("kraken/user/follows/streams", offset, limit, token);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Calculate the from id from the token
        List<User> fromUsers = getUsers(null, null, token);
        if (fromUsers.size() == 0)
            throw halt(BAD_REQUEST, "User token invalid");
        String fromId = fromUsers.get(0).getId();
        // Get follows
        List<me.philippheuer.twitch4j.model.Follow> userFollows = twitch.getUserEndpoint().getUserFollows(
                parseLong(fromId),
                Optional.of(parseLong(limit)),
                Optional.of(parseLong(offset)),
                Optional.of(direction),
                Optional.of(sortBy)
        );
        if (userFollows == null)
            throw halt(SERVER_ERROR, "Failed to connect to Twitch API");
        for (me.philippheuer.twitch4j.model.Follow follow : userFollows)
            System.out.println(follow.getChannel().getName());
        List<String> followIds = new ArrayList<>();
        for (me.philippheuer.twitch4j.model.Follow follow : userFollows)
            if (follow.getChannel().getId() != null)
                followIds.add(String.valueOf(follow.getChannel().getId()));
        @NotNull List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams =
                getStreams(getAfterFromOffset("0", limit), null, null, limit,
                        null, null, null, followIds, null);
        // Cache and return
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }
}
