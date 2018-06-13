package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FollowedGameList {
    private List<Game> follows;
    @SerializedName(value = "total", alternate = "_total")
    private long total;

    public List<Game> getFollows() {
        return follows;
    }
}
