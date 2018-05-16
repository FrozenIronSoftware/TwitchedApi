package com.rolandoislas.twitchunofficial.util;

import com.rolandoislas.twitchunofficial.TwitchUnofficialApi;
import com.rolandoislas.twitchunofficial.data.model.UsersWithRate;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.util.twitch.helix.FollowList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.rolandoislas.twitchunofficial.TwitchUnofficial.cache;

/**
 * Checks for any id that need to have their follows cached and polls the api and caches them.
 */
public class FollowsCacher implements Runnable {
    @SuppressWarnings("FieldCanBeLocal")
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
        // Fetch user data and cache from IDS
        cacheUsers(followIds);
    }

    /**
     * Fetch users by id and cache
     * Only user ids not in the cache will be fetched
     * @param ids user ids to fetch
     */
    private void cacheUsers(List<String> ids) throws InterruptedException {
        // Find non-cached users
        Map<String, String> users = cache.getUserNames(ids);
        List<String> missingFromCache = new ArrayList<>();
        for (Map.Entry<String, String> userEntry : users.entrySet()) {
            if (userEntry.getValue() == null && userEntry.getKey() != null &&
                    !missingFromCache.contains(userEntry.getKey())) {
                missingFromCache.add(userEntry.getKey());
            }
        }
        // Fetch users
        for (int idIndex = 0; idIndex < missingFromCache.size(); idIndex += 100) {
            List<String> fetchIds = missingFromCache.subList(idIndex, Math.min(idIndex, missingFromCache.size()));
            UsersWithRate usersWithRate = TwitchUnofficialApi.getUsersWithRate(fetchIds,
                    null, null, null, null);
            if (usersWithRate.getUsers() == null)
                return;
            if (usersWithRate.getRateLimit() < TwitchUnofficialApi.RATE_LIMIT_MAX / 4) {
                Logger.debug("FollowsCacher: Rate limit is low. Halting for 10 seconds");
                Thread.sleep(10000);
            }
            Thread.sleep(2000);
        }
    }
}
