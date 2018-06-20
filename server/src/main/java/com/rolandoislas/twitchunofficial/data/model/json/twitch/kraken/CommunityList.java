package com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CommunityList {
    @SerializedName(value = "cursor", alternate = "_cursor")
    private String cursor;
    @SerializedName(value = "total", alternate = "_total")
    private long total;
    private List<Community> communities;

    public List<Community> getCommunities() {
        return communities;
    }

    public void setCommunities(List<Community> communities) {
        this.communities = communities;
    }
}
