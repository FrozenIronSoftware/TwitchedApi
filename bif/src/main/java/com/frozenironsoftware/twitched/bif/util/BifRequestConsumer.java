package com.frozenironsoftware.twitched.bif.util;

import com.frozenironsoftware.twitched.bif.BifGenerator;
import com.rolandoislas.twitchunofficial.util.GoogleStorage;
import com.rolandoislas.twitchunofficial.util.Logger;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import java.util.List;

public class BifRequestConsumer {
    private GoogleStorage storage = new GoogleStorage(BifGenerator.redis);
    private BifTool bifTool = new BifTool(storage);

    /**
     * Poll the redis instance for a BIF id request
     */
    public void poll() {
        String id = getNextId();
        if (id == null)
            return;
        if (storage.containsBif(id)) {
            removeId(id);
            return;
        }
        bifTool.generateAndStoreBif(id);
        removeId(id);
    }

    /**
     * Remove an id from the queue
     * @param id id to remove
     */
    private void removeId(String id) {
        try (Jedis jedis = BifGenerator.redis.getAuthenticatedJedis()) {
            jedis.lrem(BifGenerator.queueId, 0, id);
        }
        catch (Exception e) {
            Logger.exception(e);
        }
    }

    /**
     * Get the first id in the queue
     * @return id or null if there is no id
     */
    @Nullable
    private String getNextId() {
        try (Jedis jedis = BifGenerator.redis.getAuthenticatedJedis()) {
            List<String> ids = jedis.lrange(BifGenerator.queueId, 0, 0);
            if (ids.size() != 1)
                return null;
            return ids.get(0);
        }
        catch (Exception e) {
            Logger.exception(e);
        }
        return null;
    }
}
