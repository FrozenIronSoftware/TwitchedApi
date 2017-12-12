/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.concurrent.locks.ReentrantLock;

public class ApiCache {
    private static final int TIMEOUT = 60 * 3; // Seconds before a cache value should be considered invalid
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
            redis.set(key, value);
            redis.expire(key, TIMEOUT);
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
