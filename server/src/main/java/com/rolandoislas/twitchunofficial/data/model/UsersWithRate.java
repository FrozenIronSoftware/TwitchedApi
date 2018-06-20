package com.rolandoislas.twitchunofficial.data.model;

import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.User;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A class that represents users with the remaining rate limit from twitch
 */
public class UsersWithRate {
    @Nullable private final List<User> users;
    private final int rateLimit;

    public UsersWithRate(@Nullable List<User> users, int rateLimit) {
        this.users = users;
        this.rateLimit = rateLimit;
    }

    @Nullable
    public List<User> getUsers() {
        return users;
    }

    public int getRateLimit() {
        return rateLimit;
    }
}
