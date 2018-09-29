package com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FollowedGameList {
    @Nullable private List<Game> follows;
    @SerializedName(value = "total", alternate = "_total")
    private long total;

    @Nullable
    public List<Game> getFollows() {
        return follows;
    }
}
