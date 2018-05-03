/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.rolandoislas.twitchunofficial.data.CachedStreams;
import com.rolandoislas.twitchunofficial.data.Id;
import com.rolandoislas.twitchunofficial.data.Playlist;
import com.rolandoislas.twitchunofficial.data.RokuQuality;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.data.json.AdServer;
import com.rolandoislas.twitchunofficial.data.json.AdServerList;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.FollowsCacher;
import com.rolandoislas.twitchunofficial.util.HeaderUtil;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.StringUtil;
import com.rolandoislas.twitchunofficial.util.twitch.Token;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.util.twitch.helix.FollowList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.GameViewComparator;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Pagination;
import com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.StreamUtil;
import com.rolandoislas.twitchunofficial.util.twitch.helix.StreamViewComparator;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.UserName;
import com.rolandoislas.twitchunofficial.util.twitch.AppToken;
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
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
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
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

public class TwitchUnofficialApi {
    public static final Queue<String> followIdsToCache = new ConcurrentLinkedQueue<>();
    private static final Pattern DURATION_REGEX = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)");
    static final int BAD_REQUEST = 400;
    private static final int SERVER_ERROR =  500;
    private static final int BAD_GATEWAY = 502;
    private static final String API = "https://api.twitch.tv/helix";
    private static final String API_RAW = "https://api.twitch.tv/api";
    private static final String API_USHER = "https://usher.ttvnw.net";
    public static final int RATE_LIMIT_MAX = 120;
    private static final String SUB_ONLY_VIDEO = "https://hls.twitched.org/sub_only_video_720/sub_only_video_720.m3u8";
    private static final String API_AUTH = "https://id.twitch.tv";
    static TwitchClient twitch;
    static Gson gson;
    private static OAuthCredential twitchOauth;
    private static Thread followsThread;
    private static Random random;

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
    @Contract(" -> fail")
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
     * Get stream HLS m3u8 links
     * @param request request
     * @param response response
     * @return links
     */
    @Nullable
    @Cached
    static String getHlsData(Request request, Response response) {
        checkAuth(request);
        if (request.splat().length < 1)
            return null;
        int fps = 30;
        String quality = "1080p";
        String fileName = request.splat()[0];
        String model = null;
        @Nullable String userToken = AuthUtil.extractTwitchToken(request);
        //noinspection Duplicates
        if (request.splat().length >= 4) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            quality = request.splat()[1];
            model = request.splat()[2];
            fileName = request.splat()[3];
        }
        else if (request.splat().length >= 3) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            quality = request.splat()[1];
            fileName = request.splat()[2];
        }
        else if (request.splat().length >= 2) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            fileName = request.splat()[1];
        }
        String[] split = fileName.split("\\.");
        if (split.length < 2 || !split[1].equals("m3u8"))
            return null;
        String[] idSplit = split[0].split("\\+");
        if (idSplit.length < 1)
            return null;
        String username = idSplit[0];
        if (username.startsWith(":")) {
            String userId = username.replaceFirst(":", "");
            List<User> users = getUsers(Collections.singletonList(userId), null, null);
            if (users == null || users.size() < 1)
                return null;
            username = users.get(0).getLogin();
        }
        if (username == null || username.isEmpty())
            return null;
        // Check cache
        String requestId = ApiCache.createKey("hls", fps, quality, fileName, model,
                AuthUtil.hashString(userToken, null));
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data

        // Construct template
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        // TODO When the API transitions to Helix the Authentication header will change
        if (userToken != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(new OAuthCredential(userToken));

        // Request channel token
        Token token = getVideoAccessToken(Token.TYPE.CHANNEL, username, userToken);

        // Request HLS playlist
        String hlsPlaylistUrl = String.format(API_USHER + "/api/channel/hls/%s.m3u8", username);
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Accept", "*/*"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("player", "twitchunofficialroku"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("token", token.getToken()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("sig", token.getSig()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("p",
                String.valueOf((int)(Math.random() * Integer.MAX_VALUE))));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", "any"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("allow_audio_only", "false"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("allow_source", "true"));
        ResponseEntity<String> playlist = restTemplate.exchange(hlsPlaylistUrl, HttpMethod.GET, null,
                String.class);

        // Parse playlist
        String playlistString = playlist.getBody();
        if (playlistString == null)
            return null;
        playlistString = cleanMasterPlaylist(playlistString, fps, quality, model);
        // Cache and return
        cache.set(requestId, playlistString);
        response.type("audio/mpegurl");
        return playlistString;
    }

    /**
     * Log twitch rate limit from response headers
     * @param responseEntity http response
     * @return amount of request remaining in the request window
     */
    private static int logTwitchRateLimit(@Nullable ResponseEntity responseEntity) {
        if (responseEntity == null)
            return 0;
        HttpHeaders headers = responseEntity.getHeaders();
        String limit = headers.getFirst("RateLimit-Limit");
        String remaining = headers.getFirst("RateLimit-Remaining");
        String reset = headers.getFirst("RateLimit-Reset");
        String log = String.format("Rate Limit:\n\tLimit: %s\n\tRemaining: %s,\n\tReset: %s",
                limit, remaining, reset);
        Logger.debug(log);
        return (int) StringUtil.parseLong(remaining);
    }

    /**
     * Get an access token for a channel stream or a VOD
     * @param type type of stream to get
     * @param userToken optional user provided twich oauth token
     * @return token
     */
    @NotCached
    private static Token getVideoAccessToken(Token.TYPE type, String id, @Nullable String userToken) {
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
        // TODO When the API transitions to Helix the Authentication header will change
        if (userToken != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(new OAuthCredential(userToken));
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
            if (token == null || token.getToken() == null || token.getSig() == null)
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
    @Nullable
    @Cached
    static String getVodData(Request request, Response response) {
        checkAuth(request);
        if (request.splat().length < 1)
            return null;
        int fps = 30;
        String quality = "1080p";
        String fileName = request.splat()[0];
        String model = null;
        @Nullable String userToken = AuthUtil.extractTwitchToken(request);
        //noinspection Duplicates
        if (request.splat().length >= 4) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            quality = request.splat()[1];
            model = request.splat()[2];
            fileName = request.splat()[3];
        }
        else if (request.splat().length >= 3) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            quality = request.splat()[1];
            fileName = request.splat()[2];
        }
        else if (request.splat().length >= 2) {
            fps = (int) StringUtil.parseLong(request.splat()[0]);
            fileName = request.splat()[1];
        }
        String[] split = fileName.split("\\.");
        if (split.length < 2 || !split[1].equals("m3u8"))
            return null;
        String[] idSplit = split[0].split("\\+");
        if (idSplit.length < 1)
            return null;
        String vodId = idSplit[0];
        // Check cache
        String requestId = ApiCache.createKey("vod", fps, quality, fileName, model,
                AuthUtil.hashString(userToken, null));
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Fetch live data
        RestTemplate restTemplate = twitch.getRestClient().getRestTemplate();
        // TODO When the API transitions to Helix the Authentication header will change
        if (userToken != null)
            restTemplate = twitch.getRestClient().getPrivilegedRestTemplate(new OAuthCredential(userToken));
        // Request VOD token
        Token token = getVideoAccessToken(Token.TYPE.VOD, vodId, userToken);

        // Request HLS playlist
        String hlsPlaylistUrl = String.format(API_USHER + "/vod/%s.m3u8", vodId);
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Accept", "*/*"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("nauth", token.getToken()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("nauthsig", token.getSig()));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("p",
                String.valueOf((int)(Math.random() * Integer.MAX_VALUE))));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("type", "any"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("allow_audio_only", "false"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("allow_source", "true"));
        ResponseEntity<String> playlist = restTemplate.exchange(hlsPlaylistUrl, HttpMethod.GET, null,
                String.class);

        // Redirect to sub only warning video
        if (playlist.getStatusCodeValue() == 403) {
            response.redirect(SUB_ONLY_VIDEO);
            return "";
        }

        // Parse playlist
        String playlistString = playlist.getBody();
        if (playlistString == null)
            return null;
        playlistString = cleanMasterPlaylist(playlistString, fps, quality, model);
        // Cache and return
        cache.set(requestId, playlistString);
        response.type("audio/mpegurl");
        return playlistString;
    }

    /**
     * Parse a master playlist file, removing entries based on parameters
     * If no streams match the fps and quality passed, the lowest stream will be added.
     * If the playlist could not be parsed the playlist will be returned "raw!" - Gordon Ramsay
     * Quality and FPS will be limited regardless of requested parameters if the Roku model passed is not null and that
     * model cannot perform at that quality.
     * @param playlistString raw playlist string
     * @param fps limit streams to this fps and lower
     * @param  quality limit streams to the quality or lower
     * @return clean master playlist
     */
    @NotCached
    private static String cleanMasterPlaylist(String playlistString, int fps, String quality,
                                              @Nullable String model) {
        // Determine max quality
        RokuQuality maxQuality = getMaxQualityForModel(quality, fps, model);
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
        // Create a sublist of playlists that match or are below the requested quality
        List<Playlist> playlistsMeetingQuality = new ArrayList<>();
        for (Playlist stream : playlists)
            if (((stream.getFps() == 30 && stream.isQualityOrLower(maxQuality.getMaxQuality30())) ||
                    (stream.getFps() == 60 && stream.isQualityOrLower(maxQuality.getMaxQuality60()))) &&
                    stream.getBitrate() <= maxQuality.getMaxBitrate())
                playlistsMeetingQuality.add(stream);
        // If no playlists match the quality, add the smallest
        if (playlistsMeetingQuality.size() == 0) {
            Playlist smallest = null;
            for (Playlist stream : playlists) {
                if (!stream.isVideo())
                    continue;
                if ((smallest == null || smallest.getQuality() > stream.getQuality()) &&
                        ((stream.getFps() == 30 && stream.isQualityOrLower(maxQuality.getMaxQuality30())) ||
                        stream.getFps() == 60 && stream.isQualityOrLower(maxQuality.getMaxQuality60())) &&
                        stream.getBitrate() <= maxQuality.getMaxBitrate())
                    smallest = stream;
            }
            if (smallest != null)
                playlistsMeetingQuality.add(smallest);
        }
        // Add streams to the master playlist
        for (Playlist stream : playlistsMeetingQuality) {
            if (stream.isVideo()) {
                playlist.addAll(stream.getLines());
                addedPlaylist = true;
            }
        }
        // If no playlist were added, add them all
        if (!addedPlaylist)
            return playlistString;
        // Return playlist
        StringBuilder cleanedPlaylist = new StringBuilder();
        for (String line : playlist)
            cleanedPlaylist.append(line).append("\r\n");
        return cleanedPlaylist.toString();
    }

    /**
     * Return a Playlist representing the max resolution quality for a model of roku
     * @param quality default quality. If this is less than the max for a model, it will be returned
     * @param fps default fps
     * @param model roku model in the format nnnnX
     * @return largest supported or specified quality
     */
    @NotCached
    private static RokuQuality getMaxQualityForModel(String quality, int fps, @Nullable String model) {
        int defaultQuality = (int) StringUtil.parseLong(quality.replace("p", ""));
        if (model == null)
            model = "null";
        int maxQuality30;
        int maxQuality60;
        int maxBitrate;
        final int ONE_MILLION = 1000000;
        // Big o' switch for models
        switch (model) {
            // 720 30 FPS
            case "2700X": // Tyler - Roku LT
            case "2500X": // Paolo - Roku HD
            case "2450X": // Paolo - Roku LT
            case "3000X": // Giga - Roku 2 HD
            case "2400X": // Giga - Roku LT
                maxQuality30 = 720;
                maxQuality60 = 0;
                maxBitrate = 4 * ONE_MILLION;
                break;
            // Liberty - Roku TV - Cannot play 60 FPS from Twitch
            case "5000X":
                maxQuality30 = 1080;
                maxQuality60 = 0;
                maxBitrate = 7 * ONE_MILLION;
                break;
            // 1080 60 FPS
            case "8000X": // Midland - Roku TV
            case "3800X": // Amarillo - Roku Streaming Stick
            case "3910X": // Gilbert - Roku Express Plus
            case "3900X": // Gilbert - Roku Express
            case "3710X": // Littlefield - Roku Express Plus
            case "3700X": // Littlefield - Roku Express
            case "3600X": // Briscoe - Roku Streaming Stick
                maxQuality30 = 1080;
                maxQuality60 = 1080;
                maxBitrate = 7 * ONE_MILLION;
                break;
            // 1080 30 FPS
            case "4230X": // Mustang - Roku 3
            case "4210X": // Mustang - Roku 2
            case "3500X": // Sugarland - Roku Streaming Stick
            case "2720X": // Tyler - Roku 2
            case "2710X": // Tyler - Roku 1, Roku SE
            case "4200X": // Austin - Roku 3
            case "3400X": // Jackson - Roku Streaming Stick
            case "3420X": // Jackson - Roku Streaming Stick
            case "3100X": // Giga - Roku 2 XS
            case "3050X": // Giga - Roku 2 XD
                maxQuality30 = 1080;
                maxQuality60 = 0;
                maxBitrate = 7 * ONE_MILLION;
                break;
            // 4K 60 FPS
            case "7000X": // Longview - 4K Roku TV
            case "6000X": // Ft. Worth - 4K Roku TV
            case "4660X": // Bryan - Roku Ultra
            case "3810X": // Amarillo - Roku Streaming Stick Plus
            case "4640X": // Cooper - Roku Ultra
            case "4630X": // Cooper - Roku Premier Plus
            case "4620X": // Cooper - Roku Premier
            case "4400X": // Dallas - Roku 4
                maxQuality30 = 2160;
                maxQuality60 = 2160;
                // This number could probably be higher, but no stream will reach this until 4K is supported by Twitch.
                maxBitrate = 20 * ONE_MILLION;
                break;
            // Legacy SD 30 FPS
            case "2100X":
            case "2100N":
            case "2050N":
            case "2050X":
            case "2000C":
            case "N1101":
            case "N1100":
            case "N1050":
            case "N1000":
                maxQuality30 = 480;
                maxQuality60 = 0;
                maxBitrate = 2 * ONE_MILLION;
                break;
            // Assume any new roku device can play at least 1080p 60 FPS
            default:
                maxQuality30 = defaultQuality;
                maxQuality60 = defaultQuality;
                maxBitrate = 7 * ONE_MILLION;
                break;
        }
        // Check for FPS limit
        if (fps < 60)
            maxQuality60 = 0;
        // Determine smallest quality and return
        int smallestQuality30 = Math.min(defaultQuality, maxQuality30);
        int smallestQuality60 = Math.min(defaultQuality, maxQuality60);
        return new RokuQuality(smallestQuality30, smallestQuality60, maxBitrate);
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
        // Start background thread
        TwitchUnofficialApi.followsThread = new Thread(new FollowsCacher());
        TwitchUnofficialApi.followsThread.setName("Follows Thread");
        TwitchUnofficialApi.followsThread.setDaemon(true);
        TwitchUnofficialApi.followsThread.start();
        TwitchUnofficialApi.random = new Random();
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
        String appTokenUrl = API_AUTH + "/oauth2/token";

        restTemplate.getInterceptors().add(new QueryRequestInterceptor("client_id", twitchClientId));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("client_secret", twitchClientSecret));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("grant_type", "client_credentials"));
        restTemplate.getInterceptors().add(new QueryRequestInterceptor("scope", ""));

        ResponseEntity<String> tokenResponse;
        try {
            tokenResponse = restTemplate.exchange(appTokenUrl, HttpMethod.POST, null,
                    String.class);
            logTwitchRateLimit(tokenResponse);
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
     * Request top games from the Kraken end point
     * @param limit limit
     * @param offset offset
     * @return top games
     */
    @Nullable
    @NotCached
    @Deprecated
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
    @Deprecated
    static String getCommunitiesKraken(Request request, @SuppressWarnings("unused") Response response) {
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
    @Deprecated
    static String getCommunityKraken(Request request, @SuppressWarnings("unused") Response response) {
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
    static String getStreamsHelix(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        // Params
        String after = request.queryParams("after");
        String before = request.queryParams("before");
        String community = request.queryParams("community_id");
        String first = request.queryParams("first");
        String game = request.queryParams("game_id");
        List<String> languages = new ArrayList<>();
        String streamType = request.queryParamOrDefault("type", "all");
        List<String> userIds = new ArrayList<>();
        List<String> userLogins = new ArrayList<>();
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
                if (!userIds.contains(value) && keyValue[0].equals("user_id"))
                    userIds.add(value);
                else if (!userLogins.contains(value) && keyValue[0].equals("user_login"))
                    userLogins.add(value);
                else if (!languages.contains(value) && keyValue[0].equals("language"))
                    languages.add(value);
            }
        }
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParamOrDefault("limit", "20");
        // Set after based on offset
        String afterFromOffset = getAfterFromOffset(offset, first);
        if (afterFromOffset != null)
            after = afterFromOffset;
        // Ignore languages if the limit is 1
        if (first.equals("1") && userIds.size() <= 1 && userLogins.size() <= 1)
            languages.clear();
        // Add en-gb
        if (languages.contains("en") && !languages.contains("en-gb"))
            languages.add("en-gb");
        // Check cache
        List<Object> requestParams = new ArrayList<>();
        requestParams.add(after);
        requestParams.add(before);
        requestParams.add(community);
        requestParams.add(first);
        requestParams.add(game);
        requestParams.addAll(languages);
        requestParams.add(streamType);
        requestParams.addAll(userIds);
        requestParams.addAll(userLogins);
        requestParams.add(offset);
        String requestId = ApiCache.createKey("helix/streams", requestParams);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Request live
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = getStreams(
                after,
                before,
                community == null ? null : Collections.singletonList(community),
                first,
                game == null ? null : Collections.singletonList(game),
                languages,
                streamType,
                userIds,
                userLogins
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
    @Cached
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
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = new ArrayList<>();
        if ((userIdsParam != null && userIdsParam.size() > 0) ||
                (userLoginsParam != null && userLoginsParam.size() > 0)) {
            // Check cache
            List<String> allIds = new ArrayList<>();
            if (userIdsParam != null)
                allIds.addAll(userIdsParam);
            if (userLoginsParam != null) {
                allIds.addAll(getUserIds(userLoginsParam).values());
            }
            CachedStreams cachedStreams = cache.getStreams(allIds);
            if (cachedStreams.getMissingIds().size() == 0 && cachedStreams.getMissingLogins().size() == 0)
                return cachedStreams.getStreams();
            // Some streams are missing
            streams = cachedStreams.getStreams();
            userIdsParam = cachedStreams.getMissingIds();
            userLoginsParam = cachedStreams.getMissingLogins();
        }

        // Request live
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
            logTwitchRateLimit(responseObject);
            try {
                com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList streamList = gson.fromJson(
                        responseObject.getBody(),
                        com.rolandoislas.twitchunofficial.util.twitch.helix.StreamList.class);
                streams.addAll(streamList.getStreams());
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
            }
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException)
                Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            else
                Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        }

        // Add user names and game names to data
        addNamesToStreams(streams);

        // Cache streams
        ArrayList<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> offlineAndOnlineStreams =
                new ArrayList<>(streams);
        if (userIdsParam != null) {
            for (String userId : userIdsParam) {
                if (!StreamUtil.streamListContainsId(streams, userId)) {
                    com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream =
                            new com.rolandoislas.twitchunofficial.util.twitch.helix.Stream();
                    stream.setUserId(userId);
                    stream.setUserName(new UserName("", ""));
                    stream.setOnline(false);
                    offlineAndOnlineStreams.add(stream);
                }
            }
        }
        if (userLoginsParam != null) {
            for (String userLogin : userLoginsParam) {
                if (!StreamUtil.streamListContainsLogin(streams, userLogin)) {
                    com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream =
                            new com.rolandoislas.twitchunofficial.util.twitch.helix.Stream();
                    stream.setUserId("");
                    stream.setUserName(new UserName(userLogin, ""));
                    stream.setOnline(false);
                    offlineAndOnlineStreams.add(stream);
                }
            }
        }
        cache.cacheStreams(offlineAndOnlineStreams);

        return streams;
    }

    /**
     * Checks the cache for stored user ids. If they are not found, request them and cache.
     * @param logins user logins
     * @return map of user ids <login, @nullable id>
     */
    private static Map<String, String> getUserIds(@Nullable List<String> logins) {
        Map<String, String> cachedLogins = cache.getUserIds(logins);
        List<String> missingLogins = new ArrayList<>();
        for (Map.Entry<String, String> cachedId : cachedLogins.entrySet())
            if (cachedId.getValue() == null)
                missingLogins.add(cachedId.getKey());
        List<User> users = new ArrayList<>();
        if (missingLogins.size() > 0)
            users = getUsers(null, missingLogins, null);
        if (users != null)
            for (User user : users)
                if (user.getId() != null && !user.getId().isEmpty() && user.getLogin() != null &&
                        !user.getLogin().isEmpty())
                    cachedLogins.put(user.getLogin(), user.getId());
        cache.setUserIds(cachedLogins);
        return cachedLogins;
    }

    /**
     * Add user names and game names to a list of streams
     * @param streams stream list
     */
    @NotCached
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
            Logger.exception(e);
            userNames = new HashMap<>();
        }
        Map<String, String> gameNames;
        try {
            int gameIdsCount = 0;
            for (String gameId : gameIds)
                if (gameId != null && !gameId.isEmpty())
                    gameIdsCount++;
            if (gameIdsCount > 0)
                gameNames = getGameNames(gameIds);
            else
                gameNames = new HashMap<>();
        }
        catch (HaltException | RestException e) {
            Logger.exception(e);
            gameNames = new HashMap<>();
        }
        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streams) {
            String userString = userNames.get(stream.getUserId());
            try {
                User user = gson.fromJson(userString, User.class);
                stream.setUserName(user == null || user.getDisplayName() == null || user.getDisplayName().isEmpty() ||
                        user.getLogin() == null || user.getLogin().isEmpty() ?
                        new UserName("" , "") : new UserName(user.getLogin(), user.getDisplayName()));
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                stream.setUserName(new UserName("", ""));
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
    @NotCached
    private static RestTemplate getPrivilegedRestTemplate(OAuthCredential oauth) {
        RestTemplate restTemplate = twitch.getRestClient().getPlainRestTemplate();
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Accept", "*/*"));
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Client-ID", twitch.getClientId()));
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
     * @return map <id, @Nullable name> all ids passed will be returned.
     *  in the case of the User type, the name string will be a User object as a json(gson) string
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
                try {
                    nameIdMap.put(user.getId(), gson.toJson(user));
                }
                catch (JsonSyntaxException e) {
                    Logger.exception(e);
                }
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
    @Contract("null, null -> fail")
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
            logTwitchRateLimit(responseObject);
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
     * @see TwitchUnofficialApi#getUsers(List, List, String, Request, Response)
     */
    @Contract("null, null, null -> fail")
    @NotCached
    @Nullable
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token) {
        return getUsers(userIds, userNames, token, null, null);
    }

    /**
     * Get a users from Twitch API
     * @param userIds id to poll
     * @param userNames name to poll
     * @param token oauth token to use instead of names or ids. if names or ids are not null, the token is ignored
     * @return list of users
     */
    @Contract("null, null, null, _, _ -> fail")
    @NotCached
    @Nullable
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token, @Nullable Request request,
                                       @Nullable Response response) {
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
            logTwitchRateLimit(responseObject);
            try {
                UserList userList = gson.fromJson(responseObject.getBody(), UserList.class);
                users = userList.getUsers();
            }
            catch (JsonSyntaxException ignore) {}
        }
        catch (RestClientException | RestException e) {
            if (e instanceof RestException) {
                // Handle an expired auth token gracefully
                if (((RestException) e).getRestError().getStatus() == 400 &&
                        (userIds == null || userIds.isEmpty()) && (userNames == null || userNames.isEmpty()) &&
                        token != null) {
                    // Redirect to the validate endpoint for versions prior to 1.4
                    if (request != null && response != null) {
                        ComparableVersion version = HeaderUtil.extractVersion(request);
                        if (version.compareTo(new ComparableVersion("1.4")) < 0) {
                            response.redirect("/api/link/validate");
                        }
                    }
                    users = new ArrayList<>();
                }
                else
                    Logger.warn("Request failed: " + ((RestException) e).getRestError().getMessage());
            }
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
    static String getGamesHelix(Request request, @SuppressWarnings("unused") Response response) {
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
        String limit = request.queryParamOrDefault("limit", "20");
        String userId = request.queryParams("user_id");
        String token = AuthUtil.extractTwitchToken(request);
        if ((token == null || token.isEmpty()) && (userId == null || userId.isEmpty()))
            throw halt(BAD_REQUEST, "Empty token");
        // Calculate the from id from the token
        String fromId = userId;
        if (fromId == null || userId.isEmpty()) {
            fromId = cache.getUserIdFromToken(token);
            if (fromId == null || fromId.isEmpty()) {
                List<User> fromUsers = getUsers(null, null, token);
                if (fromUsers == null || fromUsers.size() == 0)
                    throw halt(BAD_REQUEST, "User token invalid");
                fromId = fromUsers.get(0).getId();
                cache.cacheUserIdFromToken(fromId, token);
            }
        }
        response.header("Twitch-User-ID", fromId);
        // Get follows
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams =
                getUserFollowedStreamsWithTimeout(fromId, 15000, HeaderUtil.extractVersion(request));
        // Sort streams
        streams.sort(new StreamViewComparator().reversed());
        streams = streams.subList(0, Math.min((int) StringUtil.parseLong(limit), streams.size()));
        // Cache and return
        return gson.toJson(streams);
    }

    /**
     * Poll the Helix endpoint for all user follows and attempt to get live streams
     * If the timeout is reached, the fetched streams will be returned and the id will be added to the fetch queue
     * @param fromId id to get follows for
     * @param timeout timeout in milliseconds
     * @param twitchedVersion twitched version string
     * @return streams
     */
    @Cached
    private static List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> getUserFollowedStreamsWithTimeout(
            String fromId, @SuppressWarnings("SameParameterValue") int timeout,
            ComparableVersion twitchedVersion) throws HaltException {
        List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streams = new ArrayList<>();
        List<String> followIds = new ArrayList<>();
        List<Follow> followsOffline = new ArrayList<>();
        String pagination = null;
        long startTime = System.currentTimeMillis();
        boolean hasTime;
        do {
            FollowList userFollows = getUserFollows(pagination,
                    null, "100", fromId, null, true);
            if (userFollows == null || userFollows.getFollows() == null)
                throw halt(SERVER_ERROR, "Failed to connect to Twitch API");
            pagination = userFollows.getPagination() != null ? userFollows.getPagination().getCursor() : null;
            followIds.clear();
            for (Follow follow : userFollows.getFollows())
                if (follow.getToId() != null)
                    followIds.add(follow.getToId());
            if (followIds.size() > 0) {
                for (int followIndex = 0; followIndex < followIds.size(); followIndex += 100) {
                    // Check if 10 seconds has passed
                    hasTime = System.currentTimeMillis() - startTime < timeout;
                    if (!hasTime)
                        break;
                    // Get streams for a sublist of 100 or less followers
                    List<String> followsSublist = followIds.subList(followIndex,
                            Math.min(followIds.size(), followIndex + 100));
                    String first = "100";
                    String after = getAfterFromOffset("0", first);
                    // Get live data
                    // Do not fetch if the rate limit is at half and the user follows more than 300 channels
                    // Allow fetching if the rate limit is at a fourth and the user follows less than or equal to 300
                    // channels
                    if (userFollows.getRateLimitRemaining() > RATE_LIMIT_MAX / 2 ||
                            (userFollows.getRateLimitRemaining() > RATE_LIMIT_MAX / 4 &&
                                    userFollows.getTotal() <= 300)) {
                        @NotNull List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> streamSublist =
                                getStreams(after, null, null, first, null, null,
                                        null, followsSublist, null);
                        // Add streams to array
                        streams.addAll(streamSublist);
                        // Add follows to offline list
                        for (com.rolandoislas.twitchunofficial.util.twitch.helix.Stream stream : streamSublist)
                            if (stream != null && stream.getUserId() != null)
                                followIds.remove(stream.getUserId());
                        if (followIds.size() > 0) {
                            for (String followId : followIds)
                                for (Follow follow : userFollows.getFollows())
                                    if (followId != null && followId.equals(follow.getToId()))
                                        followsOffline.add(follow);
                        }
                    }
                    else {
                        Logger.extra("Denied user follow streams request.\n\tReason: Rate limit too low\n" +
                                "\tRate Limit: %d\n\tTotal Follows: %d", userFollows.getRateLimitRemaining(),
                                userFollows.getTotal());
                    }
                }
            }
            // Check if 10 seconds has passed
            hasTime = System.currentTimeMillis() - startTime < timeout;
        }
        while (followIds.size() == 100 && pagination != null && hasTime);
        // Add offline channels to list
        if (hasTime && followsOffline.size() > 0 && followsOffline.size() <= 100) {
            List<String> followIdsOffline = new ArrayList<>();
            for (Follow follow : followsOffline)
                followIdsOffline.add(follow.getToId());
            Map<String, String> offlineUsers = getUserNames(followIdsOffline);
            List<com.rolandoislas.twitchunofficial.util.twitch.helix.Stream> offlineStreams = new ArrayList<>();
            for (Map.Entry<String, String> offlineUser : offlineUsers.entrySet()) {
                if (offlineUser.getValue() == null || offlineUser.getValue().isEmpty())
                    continue;
                com.rolandoislas.twitchunofficial.util.twitch.helix.Stream offlineStream =
                        new com.rolandoislas.twitchunofficial.util.twitch.helix.Stream();
                offlineStream.setUserId(offlineUser.getKey());
                try {
                    User user = gson.fromJson(offlineUser.getValue(), User.class);
                    if (user == null || user.getLogin() == null || user.getDisplayName() == null ||
                            user.getOfflineImageUrl() == null)
                        continue;
                    offlineStream.setUserName(new UserName(user.getLogin(), user.getDisplayName()));
                    offlineStream.setThumbnailUrl(user.getOfflineImageUrl()
                            .replace("-1920x", "-{width}x")
                            .replace("x1080.", "x{height}.")
                            .replace("-1280x", "-{width}x")
                            .replace("x720.", "x{height}."));
                    offlineStream.setTitle(user.getDescription() == null ? "" : user.getDescription());
                    offlineStream.setViewerCount(user.getViewCount());
                    offlineStream.setGameName("IRL");
                    offlineStream.setGameId("494717");
                    if (twitchedVersion.compareTo(new ComparableVersion("1.3")) >= 0)
                        offlineStream.setType("user_follow");
                    else
                        offlineStream.setType("user");
                    for (Follow follow : followsOffline)
                        if (follow.getToId().equals(offlineUser.getKey()))
                            offlineStream.setStartedAt(follow.getFollowedAt());
                }
                catch (JsonSyntaxException e) {
                    Logger.exception(e);
                    continue;
                }
                offlineStreams.add(offlineStream);
            }
            streams.addAll(offlineStreams);
        }
        // Request offline user names from Redis
        // Time expired - Send the data that was retrieved and add the user id to a background thread that caches
        // follows. This is not likely to happen on accounts with less than 300 follows.
        cacheFollows(fromId);
        return streams;
    }

    /**
     * Add a user id to a list of IDs that will be used in a background thread to fetch and cache user follows
     * @param fromId user id
     * @param force ignore cache time
     */
    @NotCached
    private static void cacheFollows(String fromId, boolean force) {
        long followIdCacheTime = cache.getFollowIdCacheTime(fromId);
        // Cache for 1 hour
        if (System.currentTimeMillis() - followIdCacheTime < 60 * 60 * 1000 && !force)
            return;
        if (!followIdsToCache.contains(fromId)) {
            Logger.debug("Adding user with id %s to the follows cacher.", fromId);
            if (!followIdsToCache.offer(fromId))
                Logger.debug("Failed to add user with id %s to follows cacher queue.");
        }
        else
            Logger.debug("User with id %s already queued in the follows cacher.", fromId);
    }

    /**
     * @see TwitchUnofficialApi#cacheFollows(String, boolean)
     * Respects cache time
     * @param fromId user id
     */
    @NotCached
    private static void cacheFollows(String fromId) {
        cacheFollows(fromId, false);
    }

    /**
     * Gets user follows
     * @param after after cursor
     * @param before before cursor
     * @param first limit
     * @param fromId user id
     * @param toId user id
     * @param loadCache load results from cache
     * @return follow list
     */
    @Contract("_, _, _, null, null, _ -> fail")
    @Cached
    @Nullable
    public static FollowList getUserFollows(@Nullable String after, @Nullable String before, @Nullable String first,
                                             @Nullable String fromId, @Nullable String toId, boolean loadCache) {
       if ((fromId == null || fromId.isEmpty()) && (toId == null || toId.isEmpty()))
           throw halt(BAD_REQUEST, "Missing to or from id");
        // Check Redis cache
        if (loadCache && fromId != null) {
            List<String> cachedIds = cache.getFollows(fromId);
            if (cachedIds.size() > 0) {
                List<Follow> follows = new ArrayList<>();
                for (String id : cachedIds) {
                    Follow follow = new Follow();
                    follow.setToId(id);
                    follow.setFromId(fromId);
                    follows.add(follow);
                }
                FollowList followList = new FollowList();
                followList.setFollows(follows);
                followList.setRateLimitRemaining(RATE_LIMIT_MAX);
                return followList;
            }
        }

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
            int rateLimitRemaining = logTwitchRateLimit(responseObject);
            try {
                String json = responseObject.getBody();
                FollowList followList = gson.fromJson(json, FollowList.class);
                followList.setRateLimitRemaining(rateLimitRemaining);
                return followList;
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
    @Contract("null, _ -> null")
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
    static String getUserFollowHelix(Request request, @SuppressWarnings("unused") Response response) {
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
            if (users != null && users.size() == 1)
                fromId = users.get(0).getId();
        }
        if (toLogin != null) {
            List<User> users = getUsers(null, Collections.singletonList(toLogin), null);
            if (users != null && users.size() == 1)
                toId = users.get(0).getId();
        }
        // Check cache
        boolean loadCache = (noCache == null || !noCache.equals("true"));
        String requestId = ApiCache.createKey("helix/user/follows", after, before, first, fromId, toId);
        if (loadCache) {
            String cachedResponse = cache.get(requestId);
            if (cachedResponse != null)
                return cachedResponse;
        }
        // Get data
        FollowList followList = getUserFollows(after, before, first, fromId, toId, loadCache);
        if (followList == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Cache and return
        String json = gson.toJson(followList.getFollows());
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
    @Deprecated
    static String getSearchKraken(Request request, @SuppressWarnings("unused") Response response) {
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
    static String getTopGamesHelix(Request request, @SuppressWarnings("unused") Response response) {
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
            logTwitchRateLimit(responseObject);
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
        List<TopGame> gamesKraken = getTopGamesKraken(first, offset);
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
    static String getUsersHelix(Request request, Response response) {
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
        keyParams[0] = AuthUtil.hashString(token, null);
        System.arraycopy(ids.toArray(), 0, keyParams, 1, ids.size());
        System.arraycopy(logins.toArray(), 0, keyParams, ids.size() + 1, logins.size());
        String requestId = ApiCache.createKey("helix/users", keyParams);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null) {
            // Preload follows
            if (token != null) {
                try {
                    List<User> users = gson.fromJson(cachedResponse, new TypeToken<List<User>>() {}.getType());
                    if (users != null && users.size() == 1 && users.get(0).getId() != null)
                        cacheFollows(users.get(0).getId(), true);
                }
                catch (JsonSyntaxException e) {
                    Logger.exception(e);
                }
            }
            // Return
            return cachedResponse;
        }
        // Request live
        List<User> users = getUsers(ids, logins, token, request, response);
        if (users == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Preload follows
        if (token != null)
            if (users.size() == 1 && users.get(0).getId() != null)
                cacheFollows(users.get(0).getId(), true);
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
    static String getVideosHelix(Request request, @SuppressWarnings("unused") Response response) {
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
    @Deprecated
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
            logTwitchRateLimit(responseObject);
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
                        durationSeconds += StringUtil.parseLong(matcher.group(4));
                    // Minutes
                    if (matcher.groupCount() >= 3)
                        durationSeconds += StringUtil.parseLong(matcher.group(3)) * 60;
                    // Hours
                    if (matcher.groupCount() >= 2)
                        durationSeconds += StringUtil.parseLong(matcher.group(2)) * 60 * 60;
                    // Days
                    if (matcher.groupCount() >= 1)
                        durationSeconds += StringUtil.parseLong(matcher.group(1)) * 24 * 60 * 60;
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
     * Follow a channel
     * @param request request
     * @param response response
     * @return empty html
     */
    @NotCached
    @Deprecated
    static String followKraken(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        // Params
        String id = request.queryParams("id");
        long followIdLong = StringUtil.parseLong(id);
        if (followIdLong == 0)
            throw halt(BAD_REQUEST, "No id");
        @Nullable String token = AuthUtil.extractTwitchToken(request);
        if (token == null)
            throw halt(BAD_REQUEST, "No token");
        // Follow
        OAuthCredential oauth = new OAuthCredential(token);
        List<User> users = getUsers(null, null, token);
        if (users == null || users.size() != 1)
            throw halt(BAD_REQUEST, "Invalid token");
        oauth.setUserId(StringUtil.parseLong(users.get(0).getId()));

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
        cacheFollows(String.valueOf(oauth.getUserId()), true);
        return "{}";
    }

    /**
     * Unfollow channel
     * @param request request
     * @param response response
     * @return empty html
     */
    @NotCached
    @Deprecated
    static String unfollowKraken(Request request, @SuppressWarnings("unused") Response response) {
        checkAuth(request);
        // Params
        String id = request.queryParams("id");
        long followIdLong = StringUtil.parseLong(id);
        if (followIdLong == 0)
            throw halt(BAD_REQUEST, "No id");
        @Nullable String token = AuthUtil.extractTwitchToken(request);
        if (token == null)
            throw halt(BAD_REQUEST, "No token");
        // Unfollow
        OAuthCredential oauth = new OAuthCredential(token);
        List<User> users = getUsers(null, null, token);
        if (users == null || users.size() != 1)
            throw halt(BAD_REQUEST, "Invalid token");
        oauth.setUserId(StringUtil.parseLong(users.get(0).getId()));
        twitch.getUserEndpoint().unfollowChannel(oauth, followIdLong);
        cacheFollows(String.valueOf(oauth.getUserId()), true);
        return "{}";
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
                int adServerIndex = random.nextInt(compatibleAdServers.size());
                AdServer selectedAdServer = compatibleAdServers.get(adServerIndex);
                adServer = selectedAdServer.getUrl();
            }
        }
        // Send response
        adServerResponse.addProperty("ad_server", adServer);
        return adServerResponse.toString();
    }
}
