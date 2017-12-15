/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import com.rolandoislas.twitchunofficial.data.Id;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ApiCache {
    private static final int TIMEOUT = 60 * 3; // Seconds before a cache value should be considered invalid
    private static final int USER_NAME_TIMEOUT = 24 * 60 * 60; // 1 Day
    private static final String USER_NAME_FIELD_PREFIX = "_username_";
    private static final String GAME_NAME_FIELD_PREFIX = "_gamename_";
    private static final int GAME_NAME_TIMEOUT = 7 * 24 * 60 * 60; // 1 Week
    private Jedis redis;
    private final ReentrantLock redisLock;

    public ApiCache(String redisServer) {
        redis = new Jedis(redisServer);
        redisLock = new ReentrantLock();
    }

    /**
     * Get a value from redis
     * @param key key to get
     * @return value of key
     */

    public String get(String key) {
        redisLock.lock();
        String value = null;
        try {
            value = redis.get(key);
        }
        catch (JedisConnectionException ignore) {
            reconnect();
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        redisLock.unlock();
        return value;
    }

    /**
     * Try to reconnect to a redis server
     */
    private void reconnect() {
        Logger.warn("Attempting to reconnect  to Redis server");
        try {
            redis.disconnect();
            redis.connect();
            Logger.info("Reconnected to Redis server");
        }
        catch (Exception e) {
            Logger.warn("Failed to reconnect to Redis server");
        }
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
        redisLock.lock();
        try {
            redis.setex(key, TIMEOUT, value);
        }
        catch (JedisConnectionException ignore) {
            reconnect();
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        redisLock.unlock();
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
        redisLock.lock();
        List<String> keys = new ArrayList<>();
        ScanResult<String> scan = null;
        ScanParams params = new ScanParams();
        params.match(keyPrefix + "*");
        List<String> names = new ArrayList<>();
        try {
            do {
                scan = redis.scan(scan != null ? scan.getStringCursor() : ScanParams.SCAN_POINTER_START, params);
                keys.addAll(scan.getResult());
            }
            while (!scan.getStringCursor().equals(ScanParams.SCAN_POINTER_START));
            if (!keys.isEmpty())
                names.addAll(redis.mget(keys.toArray(new String[keys.size()])));
        }
        catch (JedisConnectionException ignore) {
            reconnect();
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        redisLock.unlock();
        // Map ids to names
        Map<String, String> nameIdMap = new HashMap<>();
        for (String id : ids) {
            String key = keyPrefix + id;
            if (!keys.contains(id)) {
                nameIdMap.put(id, null);
                continue;
            }
            String name = names.get(keys.indexOf(key));
            nameIdMap.put(id, name);
        }
        for (int idIndex = 0; idIndex < keys.size(); idIndex++) {
            String id = keys.get(idIndex).replace(keyPrefix, "");
            if (ids.contains(id))
                nameIdMap.put(id, names.get(idIndex));
        }
        return nameIdMap;
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
                keyTimeout = USER_NAME_TIMEOUT;
                break;
            case GAME:
                keyPrefix = GAME_NAME_FIELD_PREFIX;
                keyTimeout = GAME_NAME_TIMEOUT;
                break;
            default:
                throw new IllegalArgumentException("Type must be GAME or USER");
        }
        redisLock.lock();
        try {
            for (Map.Entry<String, String> nameId : namesIdMap.entrySet()) {
                if (nameId.getKey() == null || nameId.getValue() == null)
                    continue;
                String key = keyPrefix + nameId.getKey();
                long result = redis.setnx(key, nameId.getValue());
                if (result == 1)
                    redis.expire(key, keyTimeout);
            }
        }
        catch (JedisConnectionException ignore) {
            reconnect();
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        redisLock.unlock();
    }
}
