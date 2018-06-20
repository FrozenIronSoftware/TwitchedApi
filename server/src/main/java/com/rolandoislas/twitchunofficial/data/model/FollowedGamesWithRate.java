package com.rolandoislas.twitchunofficial.data.model;

import com.rolandoislas.twitchunofficial.data.model.json.twitch.helix.Game;

import java.util.List;

public class FollowedGamesWithRate {
    private final List<Game> followedGames;
    private final int rateRemaining;

    public List<Game> getFollowedGames() {
        return followedGames;
    }

    public int getRateRemaining() {
        return rateRemaining;
    }

    public FollowedGamesWithRate(List<Game> followedGames, int rateRemaining) {
        this.followedGames = followedGames;
        this.rateRemaining = rateRemaining;
    }
}
