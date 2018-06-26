/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial;

import com.goebl.david.Response;
import com.goebl.david.Webb;
import com.goebl.david.WebbException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.rolandoislas.twitchunofficial.data.annotation.Cached;
import com.rolandoislas.twitchunofficial.data.annotation.NotCached;
import com.rolandoislas.twitchunofficial.data.model.CachedStreams;
import com.rolandoislas.twitchunofficial.data.model.FollowQueue;
import com.rolandoislas.twitchunofficial.data.model.FollowedGamesWithRate;
import com.rolandoislas.twitchunofficial.data.model.Id;
import com.rolandoislas.twitchunofficial.data.model.Playlist;
import com.rolandoislas.twitchunofficial.data.model.RokuQuality;
import com.rolandoislas.twitchunofficial.data.model.TwitchCredentials;
import com.rolandoislas.twitchunofficial.data.model.UsersWithRate;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.AppToken;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.Token;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.FollowList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.GameList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.GameViewComparator;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.Pagination;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.Stream;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.StreamList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.StreamUtil;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.StreamViewComparator;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.User;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.UserList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.UserName;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Channel;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.ChannelList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Community;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.CommunityList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.FollowedGameList;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Preview;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.AuthUtil;
import com.rolandoislas.twitchunofficial.util.FollowsCacher;
import com.rolandoislas.twitchunofficial.util.HeaderUtil;
import com.rolandoislas.twitchunofficial.util.Logger;
import com.rolandoislas.twitchunofficial.util.NotFoundException;
import com.rolandoislas.twitchunofficial.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import spark.HaltException;
import spark.Request;
import spark.Spark;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

public class TwitchUnofficialApi {
    public static final Queue<FollowQueue> followIdsToCache = new ConcurrentLinkedQueue<>();
    private static final Pattern DURATION_REGEX = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)");
    private static final String IMAGE_SIZE_REGEX = "-\\d+x\\d+\\.";
    static final int BAD_REQUEST = 400;
    static final int NOT_FOUND = 404;
    static final int SERVER_ERROR =  500;
    private static final int BAD_GATEWAY = 502;
    private static final String API = "https://api.twitch.tv/helix";
    private static final String API_KRAKEN = "https://api.twitch.tv/kraken";
    private static final String API_RAW = "https://api.twitch.tv/api";
    private static final String API_USHER = "https://usher.ttvnw.net";
    public static final int RATE_LIMIT_MAX = 120;
    private static final String SUB_ONLY_VIDEO = "https://hls.twitched.org/sub_only_video_720/sub_only_video_720.m3u8";
    private static final String API_AUTH = "https://id.twitch.tv";

    static Gson gson;
    private static Thread followsThread;
    private static TwitchCredentials twitchCredentials;
    private static final Map<String, ReentrantLock> hlsLocks = Collections.synchronizedMap(new WeakHashMap<>());
    private static long lastAppTokenFetch = 0;
    private static int appTokenFetchFailures = 0;

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
    static void unauthorized() {
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
    static String getHlsData(Request request, spark.Response response) {
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
            Map<String, User> cachedUsers = getCachedUsers(Collections.singletonList(userId));
            User user = cachedUsers.get(userId);
            if (user == null)
                return null;
            username = user.getLogin();
            if (username == null || username.isEmpty()) {
                @Nullable List<User> forcedUsers = getUsers(Collections.singletonList(userId), null, null);
                if (forcedUsers != null && forcedUsers.size() == 1) {
                    User forcedUser = forcedUsers.get(0);
                    username = forcedUser.getLogin();
                }
            }
        }
        if (username == null || username.isEmpty())
            return null;
        // Check cache
        String requestId = ApiCache.createKey("hls", username, AuthUtil.hashString(userToken, null));
        ReentrantLock lock;
        synchronized (hlsLocks) {
            lock = hlsLocks.get(requestId);
            if (lock == null) {
                lock = new ReentrantLock();
                hlsLocks.put(requestId, lock);
            }
        }
        lock.lock();
        try {
            String cachedResponse = cache.get(requestId);
            if (cachedResponse != null) {
                response.type("audio/mpegurl");
                String cachedPlaylist = cleanMasterPlaylist(cachedResponse, fps, quality, model);
                return cachedPlaylist.isEmpty() ? null : cachedPlaylist;
            }
            // Get live data

            // Construct template
            Webb webb = getWebb();
            // TODO When the API transitions to Helix the Authentication header will change
            if (userToken != null)
                webb = getPrivilegedWebbKraken(userToken);

            // Request channel token
            Token token = getVideoAccessToken(Token.TYPE.CHANNEL, username, userToken);

            // Request HLS playlist
            String hlsPlaylistUrl = String.format(API_USHER + "/api/channel/hls/%s.m3u8", username);
            String playlistString = null;
            try {
                Logger.verbose("Rest Request to [%s]", hlsPlaylistUrl);
                Response<String> webbResponse = webb.get(hlsPlaylistUrl)
                        .header("Accept", "*/*")
                        .param("player", "Twitched")
                        .param("token", token.getToken())
                        .param("sig", token.getSig())
                        .param("p", String.valueOf((int) (Math.random() * Integer.MAX_VALUE)))
                        .param("type", "any")
                        .param("allow_audio_only", "true")
                        .param("allow_source", "true")
                        .ensureSuccess()
                        .asString();
                playlistString = webbResponse.getBody();
            } catch (WebbException e) {
                if (e.getResponse().getStatusCode() != 404) {
                    Logger.warn("Request failed: " + e.getMessage());
                    Logger.exception(e);
                }
            }

            // Parse playlist
            if (playlistString == null || playlistString.isEmpty())
                return null;
            // Cache and return
            cache.set(requestId, playlistString);
            response.type("audio/mpegurl");
            String cleanedPlaylist = cleanMasterPlaylist(playlistString, fps, quality, model);
            return cleanedPlaylist.isEmpty() ? null : cleanedPlaylist;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Log twitch rate limit from response headers
     * @param response Webb response
     * @return amount of request remaining in the request window
     */
    private static int logTwitchRateLimit(@Nullable Response<?> response) {
        if (response == null)
            return 0;
        String limit = response.getHeaderField("RateLimit-Limit");
        if (StringUtil.parseLong(limit) == 30 && System.currentTimeMillis() - lastAppTokenFetch >= 10000 &&
                appTokenFetchFailures < 10) {
            String appToken = getAppToken(twitchCredentials.getClientId(), twitchCredentials.getClientSecret());
            lastAppTokenFetch = System.currentTimeMillis();
            if (appToken == null)
                appTokenFetchFailures++;
            else {
                twitchCredentials.setAppToken(appToken);
                appTokenFetchFailures = 0;
            }
        }
        String remaining = response.getHeaderField("RateLimit-Remaining");
        String reset = response.getHeaderField("RateLimit-Reset");
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
        Webb webb = getWebb();
        // TODO When the API transitions to Helix the Authentication header will change
        if (userToken != null)
            webb = getPrivilegedWebbKraken(userToken);
        String tokenJsonString;
        try {
            Logger.verbose( "Rest Request to [%s]", hlsTokenUrl);
            Response<String> response = webb.get(hlsTokenUrl).ensureSuccess().asString();
            tokenJsonString = response.getBody();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            throw halt(404, "Not found");
        }
        Token token;
        try {
            token = gson.fromJson(tokenJsonString, Token.class);
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
    static String getVodData(Request request, spark.Response response) {
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
        String requestId = ApiCache.createKey("vod", vodId, AuthUtil.hashString(userToken, null));
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null) {
            response.type("audio/mpegurl");
            String cachedPlaylist = cleanMasterPlaylist(cachedResponse, fps, quality, model);
            return cachedPlaylist.isEmpty() ? null : cachedPlaylist;
        }
        // Fetch live data
        Webb webb = getWebb();
        // TODO When the API transitions to Helix the Authentication header will change
        if (userToken != null)
            webb = getPrivilegedWebbKraken(userToken);
        // Request VOD token
        Token token = getVideoAccessToken(Token.TYPE.VOD, vodId, userToken);

        // Request HLS playlist
        String hlsPlaylistUrl = String.format(API_USHER + "/vod/%s.m3u8", vodId);
        String playlistString = null;
        try {
            Logger.verbose( "Rest Request to [%s]", hlsPlaylistUrl);
            Response<String> webbResponse = webb.get(hlsPlaylistUrl)
                    .header("Accept", "*/*")
                    .param("nauth", token.getToken())
                    .param("nauthsig", token.getSig())
                    .param("p", String.valueOf((int) (Math.random() * Integer.MAX_VALUE)))
                    .param("type", "any")
                    .param("allow_audio_only", "true")
                    .param("allow_source", "true")
                    .ensureSuccess()
                    .asString();
            playlistString = webbResponse.getBody();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
            // Redirect to sub only warning video
            if (e.getResponse().getStatusCode() == 403) {
                response.redirect(SUB_ONLY_VIDEO);
                return "";
            }
        }

        // Parse playlist
        if (playlistString == null || playlistString.isEmpty())
            return null;
        // Cache and return
        cache.set(requestId, playlistString);
        response.type("audio/mpegurl");
        String cleanedPlaylist = cleanMasterPlaylist(playlistString, fps, quality, model);
        return cleanedPlaylist.isEmpty() ? null : cleanedPlaylist;
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
                                (stream.getFps() == 60 && stream.isQualityOrLower(maxQuality.getMaxQuality60()))) &&
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
            // Catch-all for Apple TVs
            case "ATV":
                maxQuality30 = 1080;
                maxQuality60 = 1080;
                maxBitrate = 7 * ONE_MILLION;
                break;
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
            // 1080 60 FPS
            case "8000X": // Midland - Roku TV
            case "3910X": // Gilbert - Roku Express Plus
            case "3900X": // Gilbert - Roku Express
            case "3710X": // Littlefield - Roku Express Plus
            case "3700X": // Littlefield - Roku Express
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
            case "5000X": // Liberty - Roku TV - gh#4
            case "3800X": // Amarillo - Roku Streaming Stick - gh#23
            case "3600X": // Briscoe - Roku Streaming Stick - gh#29
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
        TwitchUnofficialApi.twitchCredentials = new TwitchCredentials(twitchClientId, twitchClientSecret,
                getAppToken(twitchClientId, twitchClientSecret));
        if (TwitchUnofficialApi.twitchCredentials.getAppToken() == null)
            Logger.warn("No Oauth token provided. Requests will be rate limited to 30 per minute.");
        // Start background thread
        TwitchUnofficialApi.followsThread = new Thread(new FollowsCacher());
        TwitchUnofficialApi.followsThread.setName("Follows Thread");
        TwitchUnofficialApi.followsThread.setDaemon(true);
        TwitchUnofficialApi.followsThread.start();
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
        Webb webb = TwitchedApi.getWebb();
        // Request app token
        String appTokenUrl = API_AUTH + "/oauth2/token";
        String tokenJsonString;
        try {
            Logger.verbose( "Rest Request to [%s]", appTokenUrl);
            Response<String> response = webb.post(appTokenUrl)
                    .param("client_id", twitchClientId)
                    .param("client_secret", twitchClientSecret)
                    .param("grant_type", "client_credentials")
                    .param("scope", "")
                    .ensureSuccess()
                    .asString();
            logTwitchRateLimit(response);
            tokenJsonString = response.getBody();
        }
        catch (WebbException e) {
            Logger.warn(StringUtils.repeat("=", 80));
            Logger.warn("Failed to get Twitch app token!");
            Logger.warn(e.getMessage());
            Logger.warn(StringUtils.repeat("=", 80));
            Logger.exception(e);
            return null;
        }
        AppToken token;
        try {
            token = gson.fromJson(tokenJsonString, AppToken.class);
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
     * Get top communities
     * @param request request
     * @param response response
     * @return communities json
     */
    @Cached
    @Deprecated
    static String getCommunitiesKraken(Request request, @SuppressWarnings("unused") spark.Response response) {
        checkAuth(request);
        // Params
        long limit = StringUtil.parseLong(request.queryParamOrDefault("limit", "20"));
        String cursor = request.queryParams("cursor");
        String offset = request.queryParams("offset");
        if (offset != null) {
            long offsetLong = StringUtil.parseLong(offset) * limit;
            cursor = Base64.getEncoder().encodeToString(String.valueOf(offsetLong).getBytes());
        }
        boolean returnArray = Boolean.parseBoolean(request.queryParamOrDefault("array", "false"));
        // Check cache
        String requestId = ApiCache.createKey("kraken/communities/top", limit, cursor, offset, returnArray);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Request live
        Webb webb = getWebbKraken();
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        if (cursor != null)
             params.put("cursor", cursor);
        CommunityList communities = null;
        try {
            String url = API_KRAKEN + "/communities/top";
            Logger.verbose( "Rest Request to [%s]", url);
            Response<String> webbResponse = webb.get(url)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            communities = gson.fromJson(webbResponse.getBody(), CommunityList.class);
        }
        catch (WebbException | JsonSyntaxException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (communities == null || communities.getCommunities() == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Clean communities
        List<Community> cleanedCommunities = new ArrayList<>();
        for (Community community : communities.getCommunities())
            cleanedCommunities.add(cleanCommunityAvatarUrl(community));
        communities.setCommunities(cleanedCommunities);
        // Cache and return
        String json = gson.toJson(returnArray ? communities.getCommunities() : communities);
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
    static String getCommunityKraken(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        Community community = getCommunityKraken(name, id);
        if (community == null)
            throw halt(BAD_GATEWAY, "");
        String json = gson.toJson(community);
        cache.set(requestId, json);
        return json;
    }

    /**
     * Replace hardcoded width and height in the community's avatar image url with template
     * @param community community to parse
     * @return cleaned community or null
     */
    @Nullable
    private static Community cleanCommunityAvatarUrl(@Nullable Community community) {
        if (community == null)
            return null;
        if (community.getAvatarImageUrl() != null)
            community.setAvatarImageUrl(community.getAvatarImageUrl().replaceAll(IMAGE_SIZE_REGEX,
                    "-{width}x{height}."));
        return community;
    }

    /**
     * Get a specified community
     * @return community or null on error
     */
    @NotCached
    @Nullable
    @Deprecated
    static Community getCommunityKraken(@Nullable String name, @Nullable String id) {
        if (name == null && id == null)
            return null;
        // Request live
        Webb webb = getWebbKraken();
        Community community;
        try {
            if (name != null && !name.isEmpty()) {
                String url = API_KRAKEN + "/communities";
                Logger.verbose( "Rest Request to [%s]", url);
                Response<String> webbResponse = webb.get(url)
                        .param("name", name)
                        .ensureSuccess()
                        .asString();
                community = gson.fromJson(webbResponse.getBody(), Community.class);
            }
            else {
                String url = API_KRAKEN + "/communities/" + id;
                Logger.verbose( "Rest Request to [%s]", url);
                Response<String> webbResponse = webb.get(url)
                        .ensureSuccess()
                        .asString();
                community = gson.fromJson(webbResponse.getBody(), Community.class);
            }
        }
        catch (WebbException | JsonSyntaxException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
            return null;
        }
        return cleanCommunityAvatarUrl(community);
    }

    /**
     * Get streams from the helix endpoint
     * @param request request
     * @param response response
     * @return stream json with usernames added to each stream as "user_name"
     */
    @Cached
    static String getStreamsHelix(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        // Check streams cache
        if (after == null && before == null && community == null && game == null && languages.size() == 0 &&
                streamType.equals("all") && userIds.size() > 0 && userLogins.size() == 0) {
            @NotNull CachedStreams cachedStreams = cache.getStreams(userIds);
            List<Stream> cachedStreamsList =
                    cachedStreams.getStreams();
            if (cachedStreamsList != null && cachedStreamsList.size() == userIds.size()) {
                return gson.toJson(cachedStreamsList);
            }
        }
        // Check page cache
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
        requestParams.add(HeaderUtil.extractVersion(request));
        String requestId = ApiCache.createKey("helix/streams", requestParams);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;

        // Request live
        List<Stream> streams = getStreams(
                after,
                before,
                community == null ? null : Collections.singletonList(community),
                first,
                game == null ? null : Collections.singletonList(game),
                languages,
                streamType,
                userIds,
                userLogins,
                HeaderUtil.extractVersion(request)
        );

        // Cache and return
        String json = gson.toJson(streams);
        cache.set(requestId, json);
        return json;
    }

    /**
     * @see TwitchUnofficialApi#getStreams(String, String, List, String, List, List, String, List, List, ComparableVersion, Boolean)
     */
    @NotNull
    @Cached
    private static List<Stream> getStreams(
            @Nullable String after,
            @Nullable String before,
            @Nullable List<String> communities,
            @Nullable String first,
            @Nullable List<String> games,
            @Nullable List<String> languages,
            @Nullable String streamType,
            @Nullable List<String> userIdsParam,
            @Nullable List<String> userLoginsParam,
            @Nullable ComparableVersion version) {
        return getStreams(after, before, communities, first, games, languages, streamType, userIdsParam,
                userLoginsParam, version, true);
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
     * @param version twitched version from request
     * @param shouldFetchLive should the data be fetch live or only cached results returned
     * @return streams
     */
    @NotNull
    @Cached
    private static List<Stream> getStreams(
            @Nullable String after,
            @Nullable String before,
            @Nullable List<String> communities,
            @Nullable String first,
            @Nullable List<String> games,
            @Nullable List<String> languages,
            @Nullable String streamType,
            @Nullable List<String> userIdsParam,
            @Nullable List<String> userLoginsParam,
            @Nullable ComparableVersion version,
            @Nullable Boolean shouldFetchLive) {
        List<Stream> streams = new ArrayList<>();
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
            if (version != null && version.compareTo(new ComparableVersion("1.4.2400")) == 0) {
                for (Stream stream : cachedStreams.getStreams()) {
                    UserName username = stream.getUserName();
                    username.setLogin("");
                    stream.setUserName(username);
                }
            }
            if (cachedStreams.getMissingIds().size() == 0 && cachedStreams.getMissingLogins().size() == 0)
                return cachedStreams.getStreams();
            // Some streams are missing
            streams = cachedStreams.getStreams();
            userIdsParam = cachedStreams.getMissingIds();
            userLoginsParam = cachedStreams.getMissingLogins();
        }

        // If only cached data is to be returned, do not fetch live
        if (shouldFetchLive != null && !shouldFetchLive)
            return streams;

        // Request live
        // Endpoint
        String requestUrl = String.format("%s/streams", API);
        Webb webb;
        if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Parameters
        Map<String, Object> params = new HashMap<>();
        if (after != null)
            params.put("after", after);
        if (before != null)
            params.put("before", before);
        if (communities != null)
            params.put("community_id", communities);
        if (first != null)
            params.put("first", first);
        if (games != null)
            params.put("game_id", games);
        if (languages != null)
            params.put("language", languages);
        if (streamType != null)
            params.put("type", streamType);
        if (userIdsParam != null)
            params.put("user_id", userIdsParam);
        if (userLoginsParam != null)
            params.put("user_login", userLoginsParam);
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> response = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            logTwitchRateLimit(response);
            try {
                StreamList streamList = gson.fromJson(response.getBody(), StreamList.class);
                streams.addAll(streamList.getStreams());
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                throw halt(BAD_GATEWAY, e.getMessage());
            }
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
            throw halt(BAD_GATEWAY, e.getMessage());
        }

        // Add user names and game names to data
        addNamesToStreams(streams, version);

        // Cache streams
        ArrayList<Stream> offlineAndOnlineStreams =
                new ArrayList<>(streams);
        if (userIdsParam != null) {
            for (String userId : userIdsParam) {
                if (!StreamUtil.streamListContainsId(streams, userId)) {
                    Stream stream =
                            new Stream();
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
                    Stream stream =
                            new Stream();
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
     * @see TwitchUnofficialApi#addNamesToStreams(List, ComparableVersion)
     */
    @NotCached
    private static void addNamesToStreams(List<Stream>
                                                  streams) {
        addNamesToStreams(streams, null);
    }

    /**
     * Add user names and game names to a list of streams
     * @param streams stream list
     */
    @NotCached
    private static void addNamesToStreams(List<Stream>
                                                      streams, @Nullable ComparableVersion version) {
        List<String> gameIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (Stream stream : streams) {
            userIds.add(stream.getUserId());
            gameIds.add(stream.getGameId());
        }
        Map<String, User> users;
        try {
            users = getCachedUsers(userIds);
        }
        catch (HaltException | WebbException e) {
            Logger.exception(e);
            users = new HashMap<>();
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
        catch (HaltException | WebbException e) {
            Logger.exception(e);
            gameNames = new HashMap<>();
        }
        for (Stream stream : streams) {
            User user = users.get(stream.getUserId());
            boolean shouldSendLogin = version == null || version.compareTo(new ComparableVersion("1.4.2400")) != 0;
            stream.setUserName(user == null || user.getDisplayName() == null || user.getDisplayName().isEmpty() ||
                    user.getLogin() == null || user.getLogin().isEmpty() ?
                    new UserName("" , "") :
                    new UserName(shouldSendLogin ? user.getLogin() : "", user.getDisplayName()));
            String gameName = gameNames.get(stream.getGameId());
            stream.setGameName(gameName == null ? "" : gameName);
        }
    }

    /**
     * Get a rest template with the oauth token added as a bearer token
     * @param oauthToken token to add to header
     * @return rest template with bearer token
     */
    @NotCached
    private static Webb getPrivilegedWebb(String oauthToken) {
        Webb webb = getWebb();
        webb.setDefaultHeader("Authorization", String.format("Bearer %s", oauthToken));
        return webb;
    }

    /**
     * Get a webb instance with the oauth token added as an authorization token
     * @param oauthToken token
     * @return webb instance
     */
    @NotCached
    @Deprecated
    private static Webb getPrivilegedWebbKraken(String oauthToken) {
        Webb webb = getWebbKraken();
        webb.setDefaultHeader("Authorization", String.format("OAuth %s", oauthToken));
        return webb;
    }

    /**
     * Get a webb instance with client id and Twitch v5 API accept header
     * @return webb instance
     */
    @NotCached
    @Deprecated
    private static Webb getWebbKraken() {
        Webb webb = getWebb();
        webb.setDefaultHeader("Accept", "application/vnd.twitchtv.v5+json");
        return webb;
    }

    /**
     * Get a generic Webb template with twitch client ID
     * @return rest template with client id
     */
    @NotCached
    private static Webb getWebb() {
        Webb webb = TwitchedApi.getWebb();
        webb.setDefaultHeader("Accept", "*/*");
        webb.setDefaultHeader("Client-ID", getTwitchCredentials().getClientId());
        return webb;
    }

    /**
     * Get game name for ids, checking the cache first
     * @param gameIds ids
     * @return game name(value) and id(key)
     */
    @Cached
    private static Map<String, String> getGameNames(List<String> gameIds) {
        Map<String, @Nullable Game> games = getCachedGames(gameIds);
        Map<String, String> gameNames = new HashMap<>();
        for (Map.Entry<String, @Nullable Game> gameEntry : games.entrySet()) {
            if (gameEntry.getValue() == null || gameEntry.getValue().getName() == null)
                gameNames.put(gameEntry.getKey(), null);
            else
                gameNames.put(gameEntry.getKey(), gameEntry.getValue().getName());
        }
        return gameNames;
    }

    /**
     * Get games for ids, checking the cache first
     * @param gameIds ids
     * @return user(value) and ids(key) - missing games will be null
     */
    @Cached
    private static Map<String, @Nullable Game> getCachedGames(List<String> gameIds) {
        Map<String, String> gamesJson = getCachedJsonForIds(gameIds, Id.GAME, true);
        Map<String, Game> games = new HashMap<>();
        for (Map.Entry<String, String> gameJson : gamesJson.entrySet()) {
            try {
                Game game = gson.fromJson(gameJson.getValue(), Game.class);
                games.put(gameJson.getKey(), game);
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                games.put(gameJson.getKey(), null);
            }
        }
        return games;
    }

    /**
     * Get users for ids, checking the cache first
     * @param userIds ids
     * @return users(value) and ids(key) - missing users may be null
     */
    @Cached
    private static Map<String, @Nullable User> getCachedUsers(List<String> userIds, boolean shouldFetchLive) {
        Map<String, String> usersJson = getCachedJsonForIds(userIds, Id.USER, shouldFetchLive);
        Map<String, User> users = new HashMap<>();
        for (Map.Entry<String, String> userJson : usersJson.entrySet()) {
            try {
                User user = gson.fromJson(userJson.getValue(), User.class);
                users.put(userJson.getKey(), user);
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                users.put(userJson.getKey(), null);
            }
        }
        return users;
    }

    /**
     * @see #getCachedUsers(List, boolean)
     */
    @Cached
    private static Map<String, @Nullable User> getCachedUsers(List<String> userIds) {
        return getCachedUsers(userIds, true);
    }

    /**
     * Get user or game json for ids
     * @param ids user or game id - must be all one type
     * @param type type of ids
     * @return map <id, @Nullable json> all ids passed will be returned.
     *  In the case of the User type, the name string will be a User object as a json(gson) string.
     *  In the case of the Game type, the string will be a gson serialized game object.
     */
    @Cached
    private static Map<String, @Nullable String> getCachedJsonForIds(List<String> ids, Id type, boolean shouldFetchLive) {
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
        // If live data should not be fetched, return what was found
        if (!shouldFetchLive)
            return nameIdMap;
        // Find missing ids
        List<String> missingIds = new ArrayList<>();
        for (Map.Entry<String, String> nameId : nameIdMap.entrySet())
            if (nameId.getValue() == null)
                missingIds.add(nameId.getKey());
        if (missingIds.size() == 0)
            return nameIdMap;
        // Request missing ids
        if (type.equals(Id.USER)) {
            for (int idIndex = 0; idIndex < missingIds.size(); idIndex += 100) {
                List<String> idsSubList = missingIds.subList(idIndex, Math.min(idIndex + 100, missingIds.size()));
                List<User> users = getUsers(idsSubList, null, null);
                if (users == null)
                    throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
                // Store missing ids
                for (User user : users) {
                    try {
                        nameIdMap.put(user.getId(), gson.toJson(user));
                    } catch (JsonSyntaxException e) {
                        Logger.exception(e);
                    }
                }
            }
            // Ensure missing ids are cached
            for (String missingId : missingIds)
                if (nameIdMap.get(missingId) == null)
                    nameIdMap.put(missingId, gson.toJson(new User()));
            cache.setUsersJson(nameIdMap);
        }
        else if (type.equals(Id.GAME)) {
            for (int idIndex = 0; idIndex < missingIds.size(); idIndex += 100) {
                List<String> idsSubList = missingIds.subList(idIndex, Math.min(idIndex + 100, missingIds.size()));
                List<Game> games = getGames(idsSubList, null);
                if (games == null)
                    throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
                // Store missing ids
                for (Game game : games) {
                    try {
                        nameIdMap.put(game.getId(), gson.toJson(game));
                    }
                    catch (JsonSyntaxException e) {
                        Logger.exception(e);
                    }
                }
            }
            // Ensure missing ids are cached
            for (String missingId : missingIds)
                if (nameIdMap.get(missingId) == null)
                    nameIdMap.put(missingId, gson.toJson(new Game()));
            cache.setGamesJson(nameIdMap);
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
        Webb webb;
        if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Parameters
        Map<String, Object> params = new HashMap<>();
        if (ids != null)
            params.put("id", ids);
        if (names != null)
            params.put("name", names);
        // REST Request
        //noinspection Duplicates
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> response = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            logTwitchRateLimit(response);
            try {
                GameList gameList = gson.fromJson(response.getBody(), GameList.class);
                games = gameList.getGames();
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        return games;
    }

    /**
     * @see TwitchUnofficialApi#getUsers(List, List, String, Request, spark.Response)
     */
    @Contract("null, null, null -> fail")
    @NotCached
    @Nullable
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token) {
        return getUsers(userIds, userNames, token, null, null);
    }

    /**
     * @see TwitchUnofficialApi#getUsersWithRate(List, List, String, Request, spark.Response)
     */
    @Contract("null, null, null, _, _ -> fail")
    @NotCached
    @Nullable
    private static List<User> getUsers(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token, @Nullable Request request,
                                       @Nullable spark.Response response) {
        UsersWithRate usersWithRate = getUsersWithRate(userIds, userNames, token, request, response);
        return usersWithRate.getUsers();
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
    public static UsersWithRate getUsersWithRate(@Nullable List<String> userIds, @Nullable List<String> userNames,
                                       @Nullable String token, @Nullable Request request,
                                       @Nullable spark.Response response) {
        if ((userIds == null || userIds.isEmpty()) && (userNames == null || userNames.isEmpty()) && token == null)
            throw halt(BAD_REQUEST, "Bad request: missing user id or user name");
        // Request live
        List<User> users = null;
        // Endpoint
        String requestUrl = String.format("%s/users", API);
        Webb webb;
        if (token != null)
            webb = getPrivilegedWebb(token);
        else if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Parameters
        Map<String, Object> params = new HashMap<>();
        if (userIds != null)
            params.put("id", userIds);
        if (userNames != null)
            params.put("login", userNames);
        int rateLimit = 0;
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> webbResponse = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            rateLimit = logTwitchRateLimit(webbResponse);
            try {
                UserList userList = gson.fromJson(webbResponse.getBody(), UserList.class);
                users = userList.getUsers();
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (WebbException e) {
            // Handle an expired auth token gracefully
            if (e.getResponse().getStatusCode() == 400 &&
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
            else {
                Logger.warn("Request failed: " + e.getMessage());
                Logger.exception(e);
            }
        }
        if (users != null) {
            for (User user : users) {
                if (!user.verifyData()) {
                    Logger.debug(
                            "User list contained a user with data that did not verify (missing login / id).");
                    users = null;
                    break;
                }
            }
        }
        return new UsersWithRate(users, rateLimit);
    }

    /**
     * Get games from the helix endpoint
     * @param request request
     * @param response response
     * @return game json
     */
    @Cached
    static String getGamesHelix(Request request, @SuppressWarnings("unused") spark.Response response) {
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
    static String getUserFollowedStreamsHelix(Request request, spark.Response response) {
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
            User user = getUserFromToken(token);
            if (user == null || user.getId() == null || user.getId().isEmpty())
                throw halt(BAD_REQUEST, "User token invalid");
            fromId = user.getId();
        }
        response.header("Twitch-User-ID", fromId);
        // Get follows
        List<Stream> streams =
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
    private static List<Stream> getUserFollowedStreamsWithTimeout(
            String fromId, @SuppressWarnings("SameParameterValue") int timeout,
            ComparableVersion twitchedVersion) throws HaltException {
        List<Stream> streams = new ArrayList<>();
        List<String> followIds = new ArrayList<>();
        List<Follow> followsOffline = new ArrayList<>();
        String pagination = null;
        long startTime = System.currentTimeMillis();
        boolean hasTime;
        boolean shouldFetchLive = false;
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
                    // TODO send this to a background thread
                    shouldFetchLive = userFollows.getRateLimitRemaining() > RATE_LIMIT_MAX / 2 ||
                            (userFollows.getRateLimitRemaining() > RATE_LIMIT_MAX / 4 && userFollows.getTotal() <= 300);
                    @NotNull List<Stream> streamSublist =
                            getStreams(after, null, null, first, null, null,
                                    null, followsSublist, null, twitchedVersion,
                                    shouldFetchLive);
                    // Add streams to array
                    streams.addAll(streamSublist);
                    // Add follows to offline list
                    for (Stream stream : streamSublist)
                        if (stream != null && stream.getUserId() != null)
                            followIds.remove(stream.getUserId());
                    if (followIds.size() > 0) {
                        for (String followId : followIds)
                            for (Follow follow : userFollows.getFollows())
                                if (followId != null && followId.equals(follow.getToId()) &&
                                        !followsOffline.contains(follow))
                                    followsOffline.add(follow);
                    }
                    // Log that live data was not used for a request
                    if (!shouldFetchLive) {
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
        int followsOfflineMaxIndex = Math.min(followsOffline.size(), 500);
        if (followsOfflineMaxIndex == 500)
            followsOfflineMaxIndex -= streams.size();
        if (followsOfflineMaxIndex < 0)
            followsOfflineMaxIndex = 0;
        followsOffline = followsOffline.subList(0, followsOfflineMaxIndex);
        if (hasTime && followsOffline.size() > 0) {
            List<String> followIdsOffline = new ArrayList<>();
            for (Follow follow : followsOffline)
                followIdsOffline.add(follow.getToId());
            Map<String, @Nullable User> offlineUsers = getCachedUsers(followIdsOffline, shouldFetchLive);
            List<Stream> offlineStreams = new ArrayList<>();
            for (Map.Entry<String, User> offlineUser : offlineUsers.entrySet()) {
                if (offlineUser.getValue() == null)
                    continue;
                Stream offlineStream =
                        new Stream();
                offlineStream.setUserId(offlineUser.getKey());
                User user = offlineUser.getValue();
                if (user == null || user.getLogin() == null || user.getDisplayName() == null ||
                        user.getOfflineImageUrl() == null)
                    continue;
                offlineStream.setUserName(new UserName(user.getLogin(), user.getDisplayName()));
                offlineStream.setThumbnailUrl(user.getOfflineImageUrl()
                        .replaceAll(IMAGE_SIZE_REGEX, "-{width}x{height}."));
                offlineStream.setTitle(user.getDescription() == null ? "" : user.getDescription());
                offlineStream.setViewerCount(user.getViewCount());
                offlineStream.setGameName("IRL");
                offlineStream.setGameId("494717");
                offlineStream.setOnline(false);
                if (twitchedVersion.compareTo(new ComparableVersion("1.3")) >= 0)
                    offlineStream.setType("user_follow");
                else
                    offlineStream.setType("user");
                for (Follow follow : followsOffline)
                    if (follow.getToId().equals(offlineUser.getKey()))
                        offlineStream.setStartedAt(follow.getFollowedAt());
                offlineStreams.add(offlineStream);
            }
            streams.addAll(offlineStreams);
        }
        // Request offline user names from Redis
        // Time expired - Send the data that was retrieved and add the user id to a background thread that caches
        // follows. This is not likely to happen on accounts with less than 300 follows.
        cacheFollows(fromId, FollowQueue.FollowType.CHANNEL);
        return streams;
    }

    /**
     * Add a user id to a list of IDs that will be used in a background thread to fetch and cache user follows
     * @param fromId user id
     * @param force ignore cache time
     */
    @NotCached
    private static void cacheFollows(String fromId, FollowQueue.FollowType followType, boolean force) {
        long followIdCacheTime = cache.getFollowIdCacheTime(fromId, followType);
        // Cache for 1 hour
        if (System.currentTimeMillis() - followIdCacheTime < 60 * 60 * 1000 && !force)
            return;
        FollowQueue followQueue = new FollowQueue(fromId, followType);
        synchronized (followIdsToCache) {
            if (!followIdsToCache.contains(followQueue)) {
                Logger.debug("Adding user with id %s to the follows cacher.", fromId);
                if (!followIdsToCache.offer(followQueue))
                    Logger.debug("Failed to add user with id %s to follows cacher queue.");
            } else
                Logger.debug("User with id %s already queued in the follows cacher.", fromId);
        }
    }

    /**
     * @see TwitchUnofficialApi#cacheFollows(String, FollowQueue.FollowType, boolean)
     * Respects cache time
     * @param fromId user id
     */
    @NotCached
    static void cacheFollows(String fromId, FollowQueue.FollowType followType) {
        cacheFollows(fromId, followType, false);
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
                    if (toId == null || toId.isEmpty() || toId.equals(id)) {
                        Follow follow = new Follow();
                        follow.setToId(id);
                        follow.setFromId(fromId);
                        follows.add(follow);
                    }
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
        Webb webb;
        if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Parameters
        Map<String, Object> params = new HashMap<>();
        if (after != null)
            params.put("after", after);
        if (before != null)
            params.put("before", before);
        if (first != null)
            params.put("first", first);
        if (fromId != null)
            params.put("from_id", fromId);
        if (toId != null)
            params.put("to_id", toId);
        // REST Request
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> response = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            int rateLimitRemaining = logTwitchRateLimit(response);
            try {
                String json = response.getBody();
                FollowList followList = gson.fromJson(json, FollowList.class);
                followList.setRateLimitRemaining(rateLimitRemaining);
                return followList;
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (WebbException e) {
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
                        offsetLong * firstLong
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
    static String getUserFollowHelix(Request request, @SuppressWarnings("unused") spark.Response response) {
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
            Map<String, String> userIds = getUserIds(Collections.singletonList(fromLogin));
            fromId = userIds.get(fromLogin);
        }
        if (toLogin != null) {
            Map<String, String> userIds = getUserIds(Collections.singletonList(toLogin));
            toId = userIds.get(toLogin);
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
    static String getSearchKraken(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        // Check cache
        String requestId = ApiCache.createKey("kraken/search", query, type, limit, offset, hls, live);
        String cachedResponse = cache.get(requestId);
        if (cachedResponse != null)
            return cachedResponse;
        // Get live data
        String json;
        // Used by switch case
        ArrayList<String> userIds = new ArrayList<>();
        List<Stream> streamsHelix = new ArrayList<>();
        List<Game> games = new ArrayList<>();
        Webb webb = getWebbKraken();
        switch (type) {
            case "streams":
                List<com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Stream> streams = null;
                try {
                    String url = API_KRAKEN + "/search/streams";
                    Logger.verbose( "Rest Request to [%s]", url);
                    Response<String> webbResponse = webb.get(url)
                            .param("query", query)
                            .param("limit", limit)
                            .param("hls", hls)
                            .param("offset", offset)
                            .ensureSuccess()
                            .asString();
                    com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.StreamList streamList =
                            gson.fromJson(webbResponse.getBody(),
                                    com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.StreamList.class);
                    streams = streamList.getStreams();
                }
                catch (WebbException | JsonSyntaxException e) {
                    Logger.warn("Request failed: " + e.getMessage());
                    Logger.exception(e);
                }
                if (streams == null)
                    throw halt(BAD_GATEWAY, "Failed to get streams");
                for (com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Stream stream : streams)
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
                            null,
                            HeaderUtil.extractVersion(request)
                    );
                json = gson.toJson(streamsHelix);
                break;
            case "channels":
                // Get channels from search
                List<Channel> channels = null;
                try {
                    String url = API_KRAKEN + "/search/channels";
                    Logger.verbose( "Rest Request to [%s]", url);
                    Response<String> webbResponse = webb.get(url)
                            .param("query", query)
                            .param("limit", limit)
                            .param("offset", offset)
                            .ensureSuccess()
                            .asString();
                    ChannelList channelList = gson.fromJson(webbResponse.getBody(), ChannelList.class);
                    channels = channelList.getChannels();
                }
                catch (WebbException | JsonSyntaxException e) {
                    Logger.warn("Request failed: " + e.getMessage());
                    Logger.exception(e);
                }
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
                        null,
                        HeaderUtil.extractVersion(request)
                );
                // Populate Streams
                SimpleDateFormat krakenDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");
                SimpleDateFormat krakenAlternateDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
                // ISO8601
                SimpleDateFormat hexlixDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                channelToStream:
                for (Channel channel : channels) {
                    // Check if stream is live
                    for (Stream stream : streamsHelix)
                        if (String.valueOf(stream.getUserId()).equals(String.valueOf(channel.getId())))
                            continue channelToStream;
                    // Add stream from channel data
                    Stream stream = new Stream();
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
                        try {
                            Date krakenDate = krakenAlternateDateFormat.parse(String.valueOf(channel.getCreatedAt()));
                            createdAt = hexlixDateFormat.format(krakenDate);
                        }
                        catch (ParseException e) {
                            Date krakenDate = krakenDateFormat.parse(String.valueOf(channel.getCreatedAt()));
                            createdAt = hexlixDateFormat.format(krakenDate);
                        }
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
                List<com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Game> gamesKraken = null;
                try {
                    String url = API_KRAKEN + "/search/games";
                    Logger.verbose( "Rest Request to [%s]", url);
                    Response<String> webbResponse = webb.get(url)
                            .param("query", query)
                            .param("live", live)
                            .ensureSuccess()
                            .asString();
                    com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.GameList gameList =
                            gson.fromJson(webbResponse.getBody(),
                                    com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.GameList.class);
                    gamesKraken = gameList.getGames();
                }
                catch (WebbException | JsonSyntaxException e) {
                    Logger.warn("Request failed: " + e.getMessage());
                    Logger.exception(e);
                }
                // Get games from the Helix endpoint
                List<String> gameIds = new ArrayList<>();
                if (gamesKraken != null)
                    for (com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Game game : gamesKraken)
                        gameIds.add(String.valueOf(game.getId()));
                if (gameIds.size() > 0)
                    games = getGames(gameIds, null);
                // Add viewers to helix data
                if (games != null && gamesKraken != null) {
                    for (Game game : games)
                        for (com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Game gameKraken : gamesKraken)
                            if (String.valueOf(game.getId()).equals(String.valueOf(gameKraken.getId())))
                                game.setViewers((int) gameKraken.getPopularity());
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
    static String getTopGamesHelix(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        Webb webb;
        if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Params
        Map<String, Object> params = new HashMap<>();
        if (after != null)
            params.put("after", after);
        if (before != null)
            params.put("before", before);
        params.put("first", first);
        // REST Request
        List<Game> games = null;
        //noinspection Duplicates
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> webbResponse = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            logTwitchRateLimit(webbResponse);
            try {
                GameList gameList = gson.fromJson(webbResponse.getBody(), GameList.class);
                games = gameList.getGames();
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        catch (WebbException e) {
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
     * Request user info from the Helix endpoint
     * @param request request
     * @param response response
     * @return json
     */
    @Cached
    static String getUsersHelix(Request request, spark.Response response) {
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
        // Check user cache
        if (ids.size() > 0 && logins.size() == 0) {
            Map<String, @Nullable User> cachedUserNames = getCachedUsers(ids);
            List<User> cachedUsers = new ArrayList<>();
            try {
                for (Map.Entry<String, @Nullable User> userEntry: cachedUserNames.entrySet()) {
                    User user = userEntry.getValue();
                    if (user == null)
                        throw new NotFoundException("Missing cached user");
                    cachedUsers.add(user);
                }
                return gson.toJson(cachedUsers);
            }
            catch (NotFoundException | JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        // Check page cache
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
                        cacheFollows(users.get(0).getId(), FollowQueue.FollowType.CHANNEL, true);
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
            if (users.size() == 1 && users.get(0).getId() != null && ids.size() == 0 && logins.size() == 0)
                cacheFollows(users.get(0).getId(), FollowQueue.FollowType.CHANNEL, true);
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
    static String getVideosHelix(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        // Non-spec params
        String offset = request.queryParams("offset");
        if (first == null)
            first = request.queryParamOrDefault("limit", "20");
        // Set after based on offset
        String afterFromOffset = getAfterFromOffset(offset, first);
        if (afterFromOffset != null)
            after = afterFromOffset;
        // Request
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
        List<Stream> videos = getVideos(ids, userId, gameId, after,
                before, first, language, period, sort, type, HeaderUtil.extractVersion(request));
        if (videos == null)
            throw halt(BAD_GATEWAY, "Bad Gateway: Could not connect to Twitch API");
        // Add game to video
        // Only do this for a single video
        //if (ids.size() == 1 && videos.size() == 1) {
            // TODO this previously used kraken data to fetch the game information. Helix does not even have the ID
        //}
        // Cache and return
        String json = gson.toJson(videos);
        cache.set(requestId, json, ApiCache.TIMEOUT_HOUR);
        return json;
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
     * @param version requesting client version
     * @return list of videos
     */
    @NotCached
    @Nullable
    private static List<Stream> getVideos(
            @Nullable List<String> ids,
            @Nullable String userId,
            @Nullable String gameId,
            @Nullable String after,
            @Nullable String before,
            @Nullable String first,
            @Nullable String language,
            @Nullable String period,
            @Nullable String sort,
            @Nullable String type,
            @NotNull ComparableVersion version) {
        // Rest template
        Webb webb;
        if (getTwitchCredentials().getAppToken() != null)
            webb = getPrivilegedWebb(getTwitchCredentials().getAppToken());
        else
            webb = getWebb();
        // Rest URL
        String requestUrl = String.format("%s/videos", API);
        // Params
        Map<String, Object> params = new HashMap<>();
        if (ids != null)
            params.put("id", ids);
        if (userId != null)
            params.put("user_id", userId);
        if (gameId != null)
            params.put("game_id", gameId);
        if (after != null)
            params.put("after", after);
        if (before != null)
            params.put("before", before);
        if (first != null)
            params.put("first", first);
        if (language != null)
            params.put("language", language);
        if (period != null)
            params.put("period", period);
        if (sort != null)
            params.put("sort", sort);
        if (type != null)
            params.put("type", type);
        // REST Request
        List<Stream> videos = null;
        try {
            Logger.verbose( "Rest Request to [%s]", requestUrl);
            Response<String> response = webb.get(requestUrl)
                    .params(params)
                    .ensureSuccess()
                    .asString();
            logTwitchRateLimit(response);
            StreamList streamList = gson.fromJson(response.getBody(), StreamList.class);
            videos = streamList.getStreams();
            // Convert the duration string to seconds
            for (Stream video : videos) {
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
        catch (WebbException | JsonSyntaxException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        if (videos != null) {
            addNamesToStreams(videos);
            if (version.compareTo(new ComparableVersion("1.5")) >= 0) {
                for (Stream video : videos) {
                    if (video.getThumbnailUrl() != null && !video.getThumbnailUrl().isEmpty()) {
                        video.setThumbnailUrl(video.getThumbnailUrl().replace("%{width}", "{width}")
                                .replace("%{height}", "{height}"));
                    }
                }
            }
        }
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
    static String followKraken(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        User user = getUserFromToken(token);
        if (user == null || user.getId() == null || user.getId().isEmpty())
            throw halt(BAD_REQUEST, "Invalid token");
        String userId = user.getId();

        // Endpoint
        String requestUrl = String.format("%s/users/%s/follows/channels/%s", API_KRAKEN, userId, id);
        Webb webb = getPrivilegedWebbKraken(token);

        // REST Request
        try {
            Logger.verbose("Rest Request to [%s]", requestUrl);
            webb.put(requestUrl).body("").ensureSuccess().asVoid();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        cacheFollows(userId, FollowQueue.FollowType.CHANNEL, true);
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
    static String unfollowKraken(Request request, @SuppressWarnings("unused") spark.Response response) {
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
        User user = getUserFromToken(token);
        if (user == null || user.getId() == null || user.getId().isEmpty())
            throw halt(BAD_REQUEST, "Invalid token");
        String userId = user.getId();
        // Rest request
        String requestUrl = String.format("%s/users/%s/follows/channels/%s", API_KRAKEN, userId, id);
        Webb webb = getPrivilegedWebbKraken(token);
        try {
            Logger.verbose("Rest Request to [%s]", requestUrl);
            webb.delete(requestUrl).body("").ensureSuccess().asVoid();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        cacheFollows(userId, FollowQueue.FollowType.CHANNEL, true);
        return "{}";
    }

    /**
     * Getter
     * @return twitch credentials
     */
    @NotCached
    static TwitchCredentials getTwitchCredentials() {
        return TwitchUnofficialApi.twitchCredentials;
    }

    /**
     * Request followed games from the Twitch undocumented API
     * @param request request
     * @param response response
     * @return json array of followed games
     */
    @Cached
    static String getFollowedGames(Request request, spark.Response response) {
        checkAuth(request);
        // Params
        String limit = request.queryParamOrDefault("limit", "20");
        String offset = request.queryParamOrDefault("offset", "0");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            unauthorized();
        // Get user name
        @Nullable User user = getUserFromToken(token);
        if (user == null || user.getLogin() == null || user.getLogin().isEmpty())
            throw halt(SERVER_ERROR, "Failed to get user name.");
        // Check followed game cache
        List<String> cachedFollowedGameIds = cache.getFollowedGames(user.getLogin());
        if (cachedFollowedGameIds.size() > 0) {
            Map<String, @Nullable Game> followedGamesMap = getCachedGames(cachedFollowedGameIds);
            List<Game> followedGames = new ArrayList<>();
            for (@Nullable Game followedGame : followedGamesMap.values())
                if (followedGame != null)
                    followedGames.add(followedGame);
            return gson.toJson(followedGames);
        }
        // Check page cache
        String cacheId = ApiCache.createKey("games/follows", limit, offset,
                AuthUtil.hashString(token, null));
        String cachedFollowsJson = cache.get(cacheId);
        if (cachedFollowsJson != null)
            return cachedFollowsJson;
        // Request live
        List<Game> followedGames = getFollowedGames(token, user.getLogin(), StringUtil.parseLong(limit),
                StringUtil.parseLong(offset));
        // Cache
        String json = gson.toJson(followedGames);
        cache.set(cacheId, json, ApiCache.TIMEOUT_HOUR);
        return json;
    }

    /**
     * @see #getFollowedGamesWithRate(String, String, long, long)
     */
    @NotCached
    @NotNull
    private static List<Game> getFollowedGames(String token, String login, long limit, long offset) {
        FollowedGamesWithRate followedGamesWithRate = getFollowedGamesWithRate(token, login, limit, offset);
        return followedGamesWithRate.getFollowedGames();
    }

    /**
     * Get followed games from the raw twitch API
     * @param token RAW user token!
     * @return list of followed games
     */
    @NotNull
    @NotCached
    public static FollowedGamesWithRate getFollowedGamesWithRate(@Nullable String token, String userName, long limit, long offset) {
        Webb webb = getWebbKraken();
        if (token != null)
            webb = getPrivilegedWebb(token);
        String url = String.format("%s/users/%s/follows/games", API_RAW, userName);
        List<Game> followedGames = new ArrayList<>();
        // This endpoint may switch to the helix endpoint. It should log and return the actual limit
        try {
            Logger.verbose("Rest request to [%s]", url);
            Response<String> webbResponse = webb.get(url)
                    .param("limit", limit)
                    .param("offset", offset * limit)
                    .ensureSuccess()
                    .asString();
            FollowedGameList followedGameList = gson.fromJson(webbResponse.getBody(), FollowedGameList.class);
            for (com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Game follow : followedGameList.getFollows()) {
                Game game = new Game();
                game.setId(String.valueOf(follow.getId()));
                game.setName(follow.getName());
                Preview boxArt = follow.getBox();
                if (boxArt != null) {
                    game.setBoxArtUrl(boxArt.getTemplate());
                }
                game.setViewers(follow.getPopularity());
                followedGames.add(game);
            }
        }
        catch (WebbException | JsonSyntaxException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        return new FollowedGamesWithRate(followedGames, RATE_LIMIT_MAX);
    }

    /**
     * Fetch a user name from a user token
     * The endpoint returns kraken data
     * @param token RAW TOKEN!
     * @return user name
     */
    @Cached
    @Nullable
    static User getUserFromToken(String token) {
        String userId = cache.getUserIdFromToken(token);
        if (userId == null || userId.isEmpty()) {
            @Nullable List<User> users = getUsers(null, null, token);
            if (users != null && users.size() == 1)
                userId = users.get(0).getId();
            if (userId == null || userId.isEmpty())
                return null;
            cache.cacheUserIdFromToken(userId, token);
        }
        Map<String, User> users = getCachedUsers(Collections.singletonList(userId));
        User user = users.get(userId);
        if (user == null)
            return null;
        return user;
    }

    /**
     * Follow a game on the Twitch undocumented endpoint
     * @param request request
     * @param response response
     * @return empty object
     */
    @NotCached
    static String followGame(Request request, spark.Response response) {
        checkAuth(request);
        // Params
        String name = request.queryParams("name");
        // Id to name
        String id = request.queryParams("id");
        if (id != null && !id.isEmpty()) {
            Map<String, String> gameNames = getGameNames(Collections.singletonList(id));
            name = gameNames.get(id);
        }
        if (name == null || name.isEmpty())
            throw halt(BAD_REQUEST, "Missing name or id");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            throw halt(BAD_REQUEST, "No Twitch token provided");
        // Request
        @Nullable User user = getUserFromToken(token);
        if (user == null || user.getLogin() == null || user.getLogin().isEmpty())
            throw halt(SERVER_ERROR, "Failed to get user name.");
        String url = String.format("%s/users/%s/follows/games/follow", API_RAW, user.getLogin());
        Webb webb = getPrivilegedWebbKraken(token);
        try {
            Logger.verbose("Rest Request to [%s]", url);
            JsonObject body = new JsonObject();
            body.addProperty("src", "directory");
            body.addProperty("name", name);
            webb.put(url)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .ensureSuccess()
                    .asVoid();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        // Re-cache follows
        cacheFollows(user.getLogin(), FollowQueue.FollowType.GAME, true);
        return "{}";
    }

    /**
     * Unfollow a game on the twitch undocumented api
     *
     * @param request request
     * @param response response
     * @return empty object
     */
    @NotCached
    static String unfollowGame(Request request, spark.Response response) {
        checkAuth(request);
        // Params
        String name = request.queryParams("name");
        // Id to name
        String id = request.queryParams("id");
        if (id != null && !id.isEmpty()) {
            Map<String, String> gameNames = getGameNames(Collections.singletonList(id));
            name = gameNames.get(id);
        }
        if (name == null || name.isEmpty())
            throw halt(BAD_REQUEST, "Missing name or id");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            throw halt(BAD_REQUEST, "No Twitch token provided");
        // Request
        @Nullable User user = getUserFromToken(token);
        if (user == null || user.getLogin() == null || user.getLogin().isEmpty())
            throw halt(SERVER_ERROR, "Failed to get user name.");
        String url = String.format("%s/users/%s/follows/games/unfollow", API_RAW, user.getLogin());
        Webb webb = getPrivilegedWebbKraken(token);
        try {
            Logger.verbose("Rest Request to [%s]", url);
            JsonObject body = new JsonObject();
            body.addProperty("src", "directory");
            body.addProperty("name", name);
            webb.delete(url)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .ensureSuccess()
                    .asVoid();
        }
        catch (WebbException e) {
            Logger.warn("Request failed: " + e.getMessage());
            Logger.exception(e);
        }
        // Re-cache follows
        cacheFollows(user.getLogin(), FollowQueue.FollowType.GAME, true);
        return "{}";
    }

    /**
     * Checks if a user is following a game
     */
    @Cached
    static String getFollowingGame(Request request, spark.Response response) {
        checkAuth(request);
        // Params
        String name = request.queryParams("name");
        // Id to name
        String id = request.queryParams("id");
        if (id != null && !id.isEmpty()) {
            Map<String, String> gameNames = getGameNames(Collections.singletonList(id));
            name = gameNames.get(id);
        }
        if (name == null || name.isEmpty())
            throw halt(BAD_REQUEST, "Missing name or id");
        String noCache = request.queryParamOrDefault("no_cache", "false");
        String token = AuthUtil.extractTwitchToken(request);
        if (token == null || token.isEmpty())
            unauthorized();
        // Check cache
        String cacheId = ApiCache.createKey("games/following", name, AuthUtil.hashString(token, null));
        if (!Boolean.valueOf(noCache)) {
            String cachedFollowsJson = cache.get(cacheId);
            if (cachedFollowsJson != null)
                return cachedFollowsJson;
        }
        // Request live
        @Nullable User user = getUserFromToken(token);
        if (user == null || user.getLogin() == null || user.getLogin().isEmpty())
            throw halt(SERVER_ERROR, "Failed to get user name.");
        Webb webb = getPrivilegedWebbKraken(token);
        String url = String.format("%s/users/%s/follows/games/isFollowing", API_RAW, user.getLogin());
        JsonObject status = new JsonObject();
        try {
            Logger.verbose("Rest request to [%s]", url);
            Response<String> webbResponse = webb.get(url)
                    .param("name", name)
                    .ensureSuccess()
                    .asString();
            JsonObject jsonResponse = gson.fromJson(webbResponse.getBody(), JsonObject.class);
            if (jsonResponse.has("error"))
                status.addProperty("status", false);
            else
                status.addProperty("status", true);
        }
        catch (WebbException e) {
            if (e.getResponse().getStatusCode() != 404) {
                Logger.warn("Request failed: " + e.getMessage());
                Logger.exception(e);
            }
            status.addProperty("status", false);
        }
        // Cache
        String json = status.toString();
        cache.set(cacheId, json, ApiCache.TIMEOUT_HOUR);
        return json;
    }
}
