package com.rolandoislas.twitchunofficial.util;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Charsets;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

public class GoogleStorage {
    private final static String BIF_BUCKET = "static.twitched.org";
    private final Storage storage;
    private final Bucket bifBucket;
    private final ApiCache redis;

    public GoogleStorage(ApiCache redis) {
        this.redis = redis;
        String googleStorageCredentials = System.getenv("GOOGLE_STORAGE_CREDENTIALS");
        Credentials credentials;
        try {
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(googleStorageCredentials.getBytes(Charsets.UTF_8)));
        }
        catch (IOException e) {
            Logger.exception(e);
            storage = null;
            bifBucket = null;
            return;
        }
        if (credentials == null) {
            Logger.warn("Invalid Google Storage credentials");
            storage = null;
            bifBucket = null;
            return;
        }
        storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
        Bucket bifBucket = null;
        try {
            bifBucket = storage.get(BIF_BUCKET);
        }
        catch (StorageException e) {
            Logger.exception(e);
        }
        this.bifBucket = bifBucket;
    }

    /**
     * Check if a bif for the given id exists
     * @param id twitch video id
     * @return bif exists in storage for this id
     */
    public boolean containsBif(String id) {
        if (bifBucket == null)
            return true;
        String cacheId = ApiCache.createKey(ApiCache.BIF_PREFIX, id);
        String cachedData = redis.get(cacheId);
        if (cachedData != null)
            return StringUtil.parseBoolean(cachedData);
        try {
            Blob blob = bifBucket.get(String.format("bif/%s/%s.bif", id, "sd"));
            boolean exists = blob != null;
            redis.set(cacheId, exists ? "1" : "0", ApiCache.TIMEOUT_WEEK);
            return exists;
        }
        catch (StorageException e) {
            return true;
        }
    }

    /**
     * Upload a bif to the Google Storage bucket
     * @param sdBif sd bif path
     * @param hdBif hd bif path
     * @param fhdBif fhd bif path
     * @param id twitch video id
     */
    public void storeBif(Path sdBif, Path hdBif, Path fhdBif, String id) {
        if (bifBucket == null)
            return;
        try {
            bifBucket.create(String.format("bif/%s/%s.bif", id, "sd"), new FileInputStream(sdBif.toFile()));
            bifBucket.create(String.format("bif/%s/%s.bif", id, "hd"), new FileInputStream(hdBif.toFile()));
            bifBucket.create(String.format("bif/%s/%s.bif", id, "fhd"), new FileInputStream(fhdBif.toFile()));
        }
        catch (FileNotFoundException | StorageException e) {
            Logger.exception(e);
            return;
        }
        String cacheId = ApiCache.createKey(ApiCache.BIF_PREFIX, id);
        redis.set(cacheId, "1", ApiCache.TIMEOUT_WEEK);
    }
}
