package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

public class Game {
    @SerializedName(value = "id", alternate = "_id")
    private long id;
    private Preview box;
    @SerializedName("giantbomb_id")
    private long giantbombId;
    private Preview logo;
    private String name;
    private long popularity;

    public long getId() {
        return id;
    }

    public long getPopularity() {
        return popularity;
    }
}
