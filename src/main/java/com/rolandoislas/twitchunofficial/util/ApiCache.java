/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import org.jetbrains.annotations.Nullable;
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
    private static final int USERNAME_TIMEOUT = 24 * 60 * 60; // 1 Day
    private static final String USER_NAME_FIELD_PREFIX = "_username_";
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
        // Get ids stored on redis
        redisLock.lock();
        List<String> keys = new ArrayList<>();
        ScanResult<String> scan = null;
        ScanParams params = new ScanParams();
        params.match(USER_NAME_FIELD_PREFIX + "*");
        List<String> userNames = new ArrayList<>();
        try {
            do {
                scan = redis.scan(scan != null ? scan.getStringCursor() : ScanParams.SCAN_POINTER_START, params);
                keys.addAll(scan.getResult());
            }
            while (!scan.getStringCursor().equals(ScanParams.SCAN_POINTER_START));
            if (!keys.isEmpty())
                userNames.addAll(redis.mget(keys.toArray(new String[keys.size()])));
        }
        catch (JedisConnectionException ignore) {
            reconnect();
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        redisLock.unlock();
        // Map ids to user names
        Map<String, String> usernameIdMap = new HashMap<>();
        for (String id : ids) {
            String key = USER_NAME_FIELD_PREFIX + id;
            if (!keys.contains(id)) {
                usernameIdMap.put(id, null);
                continue;
            }
            String userName = userNames.get(keys.indexOf(key));
            usernameIdMap.put(id, userName);
        }
        for (int idIndex = 0; idIndex < keys.size(); idIndex++) {
            String id = keys.get(idIndex).replace(USER_NAME_FIELD_PREFIX, "");
            if (ids.contains(id))
                usernameIdMap.put(id, userNames.get(idIndex));
        }
        return usernameIdMap;
    }

    /**
     * Set user names
     * @param userNameIdMap id (key) - user name (value)
     */
    public void setUserNames(Map<String, String> userNameIdMap) {
        redisLock.lock();
        try {
            for (Map.Entry<String, String> userNameId : userNameIdMap.entrySet()) {
                String key = USER_NAME_FIELD_PREFIX + userNameId.getKey();
                long result = redis.setnx(key, userNameId.getValue());
                if (result == 1)
                    redis.expire(key, USERNAME_TIMEOUT);
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
