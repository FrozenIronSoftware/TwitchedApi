package com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken;

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
    private Object properties;
    @SerializedName(value = "links", alternate = "_links")
    private Object links;
    @SerializedName("localized_name")
    private String localizedName;
    private String locale;

    public long getId() {
        return id;
    }

    public long getPopularity() {
        return popularity;
    }

    public String getName() {
        return name;
    }

    public Preview getBox() {
        return box;
    }
}
