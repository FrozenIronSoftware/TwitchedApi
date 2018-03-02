/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.data.CachedStreams;
import com.rolandoislas.twitchunofficial.data.Id;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Stream;
import com.rolandoislas.twitchunofficial.util.twitch.helix.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.util.JedisURIHelper;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiCache {
    private static final int TIMEOUT = 60 * 3; // Seconds before a cache value should be considered invalid
    private static final int TIMEOUT_HOUR = 60 * 60;
    private static final int TIMEOUT_DAY = 24 * 60 * 60; // 1 Day
    private static final int TIMEOUT_WEEK = 7 * 24 * 60 * 60; // 1 Week
    private static final String USER_NAME_FIELD_PREFIX = "_username_";
    private static final String GAME_NAME_FIELD_PREFIX = "_gamename_";
    public static final String LINK_PREFIX = "_link_";
    public static final String TOKEN_PREFIX = "_token_";
    private static final String FOLLOW_PREFIX = "_follow_";
    private static final String FOLLOW_TIME_PREFIX = "_follow_time_";
    private static final String STREAM_PREFIX = "_stream_";
    private static final String TOKEN_ID_PREFIX = "_token_id_";
    private final String redisPassword;
    private final Gson gson;
    private JedisPool redisPool;

    public ApiCache(String redisServer) {
        String connectionLimit = System.getenv("REDIS_CONNECTIONS");
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setBlockWhenExhausted(true);
        if (connectionLimit != null && !connectionLimit.isEmpty())
            poolConfig.setMaxTotal((int) StringUtil.parseLong(connectionLimit));
        else
            poolConfig.setMaxTotal(1);
        // Create the pool
        URI uri = URI.create(redisServer);
        if (JedisURIHelper.isValid(uri)) {
            String host = uri.getHost();
            int port = uri.getPort();
            redisPassword = JedisURIHelper.getPassword(uri);
            redisPool = new JedisPool(poolConfig, host, port, Protocol.DEFAULT_TIMEOUT, redisPassword,
                    Protocol.DEFAULT_DATABASE, null);
        }
        else {
            redisPool = new JedisPool();
            redisPassword = "";
        }
        gson = new Gson();
    }

    /**
     * Get a value from redis
     * @param key key to get
     * @return value of key
     */
    @Nullable
    public String get(String key) {
        String value = null;
        try (Jedis redis = getAuthenticatedJedis()) {
            value = redis.get(key);
        } catch (Exception e) {
            Logger.exception(e);
        }
        return value;
    }

    private Jedis getAuthenticatedJedis() {
        Jedis jedis = redisPool.getResource();
        if (!redisPassword.isEmpty())
        jedis.auth(redisPassword);
        if (!jedis.isConnected())
            jedis.connect();
        return jedis;
    }

    /**
     * Create a key for an endpoint request with its parameters
     * @param endpoint API endpoint called
     * @param params parameters passed to endpoint
     * @return key
     */
    public static String createKey(String endpoint, Object... params) {
        StringBuilder key = new StringBuilder(endpoint);
        String separator = "?";
        for (Object param : params) {
            key.append(separator).append(String.valueOf(param));
            separator = "&";
        }
        return key.toString();
    }

    /**
     * Set a key with the default cache timeout
     * @param key key to set
     * @param value value to set the key
     */
    public void set(String key, String value) {
        try (Jedis redis = getAuthenticatedJedis()) {
            redis.setex(key, TIMEOUT, value);
        } catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Get user names from Redis.
     * Any user names that do not exist will be requested in a bulk request from twitch
     * @param ids ids to convert to user names
     * @return map with ids as keys and user names as values - user names not in cache will be null
     */
    public Map<String, String> getUserNames(List<String> ids) {
        return getNamesForIds(ids, Id.USER);
    }

    /**
     * Set user names
     * @param userNameIdMap id (key) - user name (value)
     */
    public void setUserNames(Map<String, String> userNameIdMap) {
        setNames(userNameIdMap, Id.USER);
    }

    /**
     * Get game names from Redis.
     * Any game names that do not exist will be requested in a bulk request from twitch
     * @param ids ids to convert to game names
     * @return map with ids as keys and game names as values - game names not in cache will be null
     */
    public Map<String, String> getGameNames(List<String> ids) {
        return getNamesForIds(ids, Id.GAME);
    }

    /**
     * Get game or id names from Redis.
     * Any names that do not exist will be requested in a bulk request from twitch
     * @param ids ids to convert to names
     * @return map with ids as keys and names as values - names not in cache will be null
     */
    private Map<String, String> getNamesForIds(List<String> ids, Id type) {
        // Determine key prefix
        String keyPrefix;
        switch (type) {
            case USER:
                keyPrefix = USER_NAME_FIELD_PREFIX;
                break;
            case GAME:
                keyPrefix = GAME_NAME_FIELD_PREFIX;
                break;
            default:
                throw new IllegalArgumentException("Type must be GAME or USER");
        }
        // Get ids stored on redis
        Map<String, String> cachedNames = mgetWithPrefix(keyPrefix, ids);
        // Map ids to names
        Map<String, String> nameIdMap = new HashMap<>();
        for (String id : ids) {
            String key = keyPrefix + id;
            nameIdMap.put(id, cachedNames.getOrDefault(key, null));
        }
        return nameIdMap;
    }

    /**
     * Get multiple keys each with a common prefix
     * @param keyPrefix prefix to be added to all keys
     * @param keys keys to get
     * @return map with redis keys as the key and possibly null value if key did not exist
     */
    private Map<String, String> mgetWithPrefix(String keyPrefix, List<String> keys) {
        Map<String, String> map = new HashMap<>();
        String[] prefixedKeys = new String[keys.size()];
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++)
            prefixedKeys[keyIndex] = keyPrefix + String.valueOf(keys.get(keyIndex));
        List<String> values = new ArrayList<>();
        if (keys.size() > 0) {
            try (Jedis redis = getAuthenticatedJedis()) {
                values.addAll(redis.mget(prefixedKeys));
            } catch (Exception e) {
                Logger.exception(e);
            }
        }
        if (values.size() != prefixedKeys.length)
            for (String key : prefixedKeys)
                map.put(key, null);
        else
            for (int keyIndex = 0; keyIndex < prefixedKeys.length; keyIndex++)
                map.put(prefixedKeys[keyIndex], values.get(keyIndex));
        return map;
    }

    /**
     * Set user names
     * @param gameNames id (key) - user name (value)
     */
    public void setGameNames(Map<String, String> gameNames) {
        setNames(gameNames, Id.GAME);
    }

    /**
     * Store names in a map to their key id
     * @param namesIdMap <id, name>
     */
    private void setNames(Map<String, String> namesIdMap, Id type) {
        // Determine key prefix
        String keyPrefix;
        int keyTimeout;
        switch (type) {
            case USER:
                keyPrefix = USER_NAME_FIELD_PREFIX;
                keyTimeout = TIMEOUT_DAY;
                break;
            case GAME:
                keyPrefix = GAME_NAME_FIELD_PREFIX;
                keyTimeout = TIMEOUT_WEEK;
                break;
            default:
                throw new IllegalArgumentException("Type must be GAME or USER");
        }
        try (Jedis redis = getAuthenticatedJedis()) {
            for (Map.Entry<String, String> nameId : namesIdMap.entrySet()) {
                if (nameId.getKey() == null || nameId.getValue() == null)
                    continue;
                String key = keyPrefix + nameId.getKey();
                long result = redis.setnx(key, nameId.getValue());
                if (result == 1)
                    redis.expire(key, keyTimeout);
            }
        } catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Attempt to remove a key
     * Fails silently
     * @param key key to remove
     */
    public Long remove(String key) {
        long ret = 0L;
        try (Jedis redis = getAuthenticatedJedis()) {
            ret = redis.del(key);
        } catch (Exception e) {
            Logger.exception(e);
        }
        return ret;
    }

    /**
     * Check if a link id exists
     * @param linkId id to check for
     * @return id exists in cache
     */
    public boolean containsLinkId(String linkId) {
        // Check for links
        Map<String, String> ids = scan(LINK_PREFIX + "*");
        for (String id : ids.values())
            if (id.equalsIgnoreCase(linkId))
                return true;
        // Check tokens
        Map<String, String> tokens = scan(TOKEN_PREFIX + "*");
        for (String tokenKey : tokens.keySet())
            if (tokenKey.replace(TOKEN_PREFIX, "").equalsIgnoreCase(linkId))
                return true;
        return false;
    }

    /**
     * Scan for keys matching a query.
     * @param query keys must match this - can include wild cards
     * @return map of keys and values
     */
    private Map<String, String> scan(String query) {
        // Scan
        List<String> keys = new ArrayList<>();
        ScanResult<String> scan = null;
        ScanParams params = new ScanParams();
        params.match(query);
        List<String> values = new ArrayList<>();
        try (Jedis redis = getAuthenticatedJedis()) {
            do {
                scan = redis.scan(scan != null ? scan.getStringCursor() : ScanParams.SCAN_POINTER_START, params);
                keys.addAll(scan.getResult());
            }
            while (!scan.getStringCursor().equals(ScanParams.SCAN_POINTER_START));
            if (!keys.isEmpty())
                values.addAll(redis.mget(keys.toArray(new String[keys.size()])));
        } catch (Exception e) {
            Logger.exception(e);
        }
        // Construct the map
        Map<String, String> map = new HashMap<>();
        Iterator<String> keysIter = keys.iterator();
        Iterator<String> valuesIter = values.iterator();
        while (keysIter.hasNext())
            map.put(keysIter.next(), valuesIter.next());
        return map;
    }

    /**
     * Get cached follows for an id
     * @param fromId id to get follows for
     * @return followed ids
     */
    public List<String> getFollows(String fromId) {
        List<String> followsList = new ArrayList<>();
        try (Jedis redis = getAuthenticatedJedis()) {
            Set<String> follows = redis.smembers(FOLLOW_PREFIX + fromId);
            followsList.addAll(follows);
        } catch (Exception e) {
            Logger.exception(e);
        }
        return followsList;
    }

    /**
     * Set follows for a user id
     * @param fromId user to set follows for
     * @param toIds id the user follows
     */
    public void setFollows(String fromId, List<String> toIds) {
        // Remove old follows
        List<String> follows = getFollows(fromId);
        try (Jedis redis = getAuthenticatedJedis()) {
            if (follows.size() > 0)
                redis.srem(FOLLOW_PREFIX + fromId, follows.toArray(new String[follows.size()]));
        } catch (Exception e) {
            Logger.exception(e);
        }
        // Set follows
        try (Jedis redis = getAuthenticatedJedis()) {
            if (toIds.size() > 0) {
                redis.sadd(FOLLOW_PREFIX + fromId, toIds.toArray(new String[toIds.size()]));
                redis.expire(FOLLOW_PREFIX + fromId, TIMEOUT_WEEK);
            }
            redis.setex(FOLLOW_TIME_PREFIX + fromId, TIMEOUT_HOUR, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Get the time that a follows set was set
     * @param fromId id
     * @return last set time of follows set for an id
     */
    public long getFollowIdCacheTime(String fromId) {
        long time = 0;
        try (Jedis redis = getAuthenticatedJedis()) {
            String timeString = redis.get(FOLLOW_TIME_PREFIX + fromId);
            if (timeString != null && !timeString.isEmpty()) {
                try {
                    time =  Long.parseLong(timeString);
                }
                catch (NumberFormatException ignore) {}
            }
        } catch (Exception e) {
            Logger.exception(e);
        }
        return time;
    }

    /**
     * Caches a list of streams
     * @param streams streams to cache
     */
    public void cacheStreams(List<Stream> streams) {
        try (Jedis redis = getAuthenticatedJedis()) {
            for (Stream stream : streams) {
                if (stream == null || ((stream.getUserId() == null || stream.getUserName() == null ||
                        stream.getUserName().getLogin() == null) && stream.isOnline()))
                    continue;
                String json = gson.toJson(stream);
                String id = String.format("%s%s_%s", STREAM_PREFIX, stream.getUserId(),
                        stream.getUserName().getLogin());
                redis.setex(id, TIMEOUT, json);
            }
        }
        catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Get streams from the cache
     * @param userIds optional ids to look for
     * @param userLogins optional logins to look for
     * @return cached streams object containing any missing ids/login and all found streams
     */
    @NotNull
    public CachedStreams getStreams(@Nullable List<String> userIds, @Nullable List<String> userLogins) {
        CachedStreams cachedStreams = new CachedStreams();
        List<Stream> offlineStreams = new ArrayList<>();
        // Find matching streams
        List<String> combinedIdsLogins = new ArrayList<>();
        if (userIds != null)
            combinedIdsLogins.addAll(userIds);
        if (userLogins != null)
            combinedIdsLogins.addAll(userLogins);
        Map<String, String> streams = mgetWithPrefix(STREAM_PREFIX, combinedIdsLogins);
        for (Map.Entry<String, String> streamEntry : streams.entrySet()) {
            try {
                Stream stream = gson.fromJson(streamEntry.getValue(), Stream.class);
                if (stream != null && !stream.isOnline()) {
                    offlineStreams.add(stream);
                    continue;
                }
                if (stream == null || stream.getUserId() == null || stream.getUserName() == null ||
                        stream.getUserName().getLogin() == null ||
                        stream.getUserName().getLogin().isEmpty())
                    continue;
                if ((userIds != null && userIds.contains(stream.getUserId())) ||
                        (userLogins != null && userLogins.contains(stream.getUserName().getLogin())))
                    cachedStreams.getStreams().add(stream);
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
            }
        }
        // Populate missing lists
        if (userIds != null)
            for (String id : userIds)
                if (!StreamUtil.streamListContainsId(cachedStreams.getStreams(), id) &&
                        !StreamUtil.streamListContainsId(offlineStreams, id))
                    cachedStreams.getMissingIds().add(id);
        if (userLogins != null)
            for (String login : userLogins)
                if (!StreamUtil.streamListContainsLogin(cachedStreams.getStreams(), login) &&
                        !StreamUtil.streamListContainsLogin(offlineStreams, login))
                    cachedStreams.getMissingLogins().add(login);
        return cachedStreams;
    }

    /**
     * Cache a SHA1 salted token with its corresponding user id
     * @param id user id
     * @param token RAW TOKEN!
     */
    public void cacheUserIdFromToken(String id, String token) {
        String tokenHash = AuthUtil.hashString(token, null);
        try (Jedis redis = getAuthenticatedJedis()) {
            redis.setex(TOKEN_ID_PREFIX + tokenHash, TIMEOUT_DAY, id);
        }
        catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Get cached user id from a user access token token
     * @param token RAW TOKEN!
     * @return id or null if not in cache
     */
    @Nullable
    public String getUserIdFromToken(String token) {
        String tokenHash = AuthUtil.hashString(token, null);
        return get(TOKEN_ID_PREFIX + tokenHash);
    }
}
