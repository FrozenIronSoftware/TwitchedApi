package com.rolandoislas.twitchunofficial.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rolandoislas.twitchunofficial.TwitchUnofficialApi;
import com.rolandoislas.twitchunofficial.data.model.FollowQueue;
import com.rolandoislas.twitchunofficial.data.model.FollowedGamesWithRate;
import com.rolandoislas.twitchunofficial.data.model.UsersWithRate;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Follow;
import com.rolandoislas.twitchunofficial.util.twitch.helix.FollowList;
import com.rolandoislas.twitchunofficial.util.twitch.helix.Game;
import com.rolandoislas.twitchunofficial.util.twitch.helix.User;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
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
    private Gson gson = new Gson();

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
        FollowQueue followQueue = TwitchUnofficialApi.followIdsToCache.element();
        String fromId = followQueue.getUserId();
        if (fromId != null && followQueue.getFollowType() != null) {
            switch (followQueue.getFollowType()) {
                case CHANNEL:
                    getFollowsForId(fromId);
                    break;
                case GAME:
                    getFollowedGamesForId(fromId);
                    break;
            }
        }
        TwitchUnofficialApi.followIdsToCache.remove();
    }

    /**
     * Get the games that a user follows
     * @param fromUserName user name to get follows for
     */
    private void getFollowedGamesForId(String fromUserName) throws InterruptedException {
        Logger.debug("FollowsCacher: Getting followed games for user id %s.", fromUserName);
        List<Game> followedGames = new ArrayList<>();
        long limit = 100;
        long offset = 0;
        boolean hasNext = true;
        do {
            @NotNull FollowedGamesWithRate followedGamesWithRate =
                    TwitchUnofficialApi.getFollowedGamesWithRate(null, fromUserName, limit, offset);
            List<Game> followedGameSublist = followedGamesWithRate.getFollowedGames();
            if (followedGameSublist.size() < limit)
                hasNext = false;
            followedGames.addAll(followedGameSublist);
            offset++;
            Thread.sleep(1000);
            // Rate limit is low
            if (followedGamesWithRate.getRateRemaining() < TwitchUnofficialApi.RATE_LIMIT_MAX / 4) {
                Logger.debug("FollowsCacher: Rate limit is low. Halting for 10 seconds");
                Thread.sleep(10000);
            }
        }
        while (hasNext);
        // Cache followed games
        List<String> followedIds = new ArrayList<>();
        for (Game game : followedGames)
            if (game.getId() != null)
                followedIds.add(game.getId());
        cache.setFollowedGames(fromUserName, followedIds);
        // Cache games
        Map<String, String> gamesJson = new HashMap<>();
        for (Game game : followedGames) {
            if (game.getId() == null)
                continue;
            try {
                gamesJson.put(game.getId(), gson.toJson(game));
            }
            catch (JsonSyntaxException e) {
                Logger.exception(e);
                try {
                    gamesJson.put(game.getId(), gson.toJson(new Game()));
                }
                catch (JsonSyntaxException ee) {
                    Logger.exception(ee);
                }
            }
        }
        cache.setGamesJson(gamesJson);
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
        Logger.debug("FollowsCacher: Fetching %d users from ids", missingFromCache.size());
        for (int idIndex = 0; idIndex < missingFromCache.size(); idIndex += 100) {
            List<String> fetchIds = missingFromCache.subList(idIndex, Math.min(idIndex, missingFromCache.size()));
            if (fetchIds.size() > 0) {
                UsersWithRate usersWithRate = TwitchUnofficialApi.getUsersWithRate(fetchIds,
                        null, null, null, null);
                if (usersWithRate.getUsers() == null)
                    return;
                List<User> fetchedUsers = usersWithRate.getUsers();
                Map<String, String> userIdMap = new HashMap<>();
                for (User fetchedUser : fetchedUsers) {
                    try {
                        String userString = gson.toJson(fetchedUser);
                        userIdMap.put(fetchedUser.getId(), userString);
                    } catch (JsonSyntaxException e) {
                        Logger.exception(e);
                    }
                }
                cache.setUsersJson(userIdMap);
                if (usersWithRate.getRateLimit() < TwitchUnofficialApi.RATE_LIMIT_MAX / 4) {
                    Logger.debug("FollowsCacher: Rate limit is low. Halting for 10 seconds");
                    Thread.sleep(10000);
                }
                Thread.sleep(2000);
            }
        }
    }
}
