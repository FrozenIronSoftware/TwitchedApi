package com.rolandoislas.twitchunofficial.util;

import com.rolandoislas.twitchunofficial.TwitchUnofficialApi;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.util.twitch.helix.FollowList;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

/**
 * Checks for any id that need to have their follows cached and polls the api and caches them.
 */
public class FollowsCacher implements Runnable {
    private boolean running = false;

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                cacheFollows();
            }
            catch (InterruptedException e) {
                Logger.exception(e);
            }
            // Empty queue, wait and try again
            catch (NoSuchElementException e) {
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e1) {
                    Logger.exception(e);
                }
            }
        }
    }

    /**
     * Check for id to get follow and cache to Redis
     */
    private void cacheFollows() throws InterruptedException, NoSuchElementException {
        // Get follows for id
        String fromId = TwitchUnofficialApi.followIdsToCache.element();
        getFollowsForId(fromId);
        TwitchUnofficialApi.followIdsToCache.remove();
    }

    /**
     * Get all follows for an id and save to the cache
     * @param fromId id
     */
    private void getFollowsForId(String fromId) throws InterruptedException {
        Logger.debug("FollowsCacher: Getting follows for user id %s.", fromId);
        List<String> followIds = new ArrayList<>();
        // Get all follows
        String pagination = null;
        int followAmount = 0;
        do {
            FollowList userFollows = TwitchUnofficialApi.getUserFollows(pagination,
                    null, "100", fromId, null, false);
            if (userFollows != null && userFollows.getFollows() != null) {
                pagination = userFollows.getPagination() != null ? userFollows.getPagination().getCursor() : null;
                followAmount = userFollows.getFollows().size();
                for (Follow follow : userFollows.getFollows())
                    if (follow.getToId() != null)
                        followIds.add(follow.getToId());
                // Rate limit is low
                if (userFollows.getRateLimitRemaining() < TwitchUnofficialApi.RATE_LIMIT_MAX / 4) {
                    Logger.debug("FollowsCacher: Rate limit is low. Halting for 60 seconds");
                    Thread.sleep(60000);
                }
            }
            else
                pagination = null;
            Thread.sleep(2000);
        }
        while (followAmount == 100 && pagination != null);
        // Cache follows
        cache.setFollows(fromId, followIds);
    }
}
