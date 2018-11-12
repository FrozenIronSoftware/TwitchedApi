package com.frozenironsoftware.twitched.bif;

import com.frozenironsoftware.twitched.bif.data.Constants;
import com.frozenironsoftware.twitched.bif.util.BifRequestConsumer;
import com.rolandoislas.twitchunofficial.util.ApiCache;
import com.rolandoislas.twitchunofficial.util.Logger;

import java.util.logging.Level;

public class BifGenerator {
    public static ApiCache redis;
    public static String queueId;
    public static String twitchedClientId;

    public static void main(String[] args) throws InterruptedException {
        Logger.setLevel(Level.ALL);
        Logger.info("%s version %s starting", Constants.NAME, Constants.VERSION);
        // Redis address
        String redisUrlEnv = System.getenv("REDIS_URL_ENV");
        String redisUrlEnvName = redisUrlEnv == null || redisUrlEnv.isEmpty() ? "REDIS_URL" : redisUrlEnv;
        String redisServer = System.getenv(redisUrlEnvName);
        if (redisServer == null || redisServer.isEmpty()) {
            Logger.warn("Missing env: %s", redisUrlEnvName);
            System.exit(1);
        }
        redis = new ApiCache(redisServer);
        // Queue id
        queueId = System.getenv("BIF_QUEUE_ID");
        if (queueId == null || queueId.isEmpty()) {
            Logger.warn("Missing env: BIF_QUEUE_ID");
            System.exit(1);
        }
        // Twitched client id
        twitchedClientId = System.getenv("TWITCHED_CLIENT_ID");
        if (twitchedClientId == null || twitchedClientId.isEmpty()) {
            Logger.warn("Missing env: TWITCHED_CLIENT_ID");
            System.exit(1);
        }
        // Google Storage Credentials
        String googleStorageCredentials = System.getenv("GOOGLE_STORAGE_CREDENTIALS");
        if (googleStorageCredentials == null || googleStorageCredentials.isEmpty()) {
            Logger.warn("Missing env: GOOGLE_STORAGE_CREDENTIALS");
            System.exit(1);
        }
        // Start
        BifRequestConsumer bifRequestConsumer = new BifRequestConsumer();
        while (true) {
            bifRequestConsumer.poll();
            Thread.sleep(1000);
        }
    }
}
