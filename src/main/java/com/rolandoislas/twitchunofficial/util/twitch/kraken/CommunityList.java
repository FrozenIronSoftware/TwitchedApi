package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CommunityList {
    @SerializedName(value = "cursor", alternate = "_cursor")
    private String cursor;
    @SerializedName(value = "total", alternate = "_total")
    private long total;
    private List<Community> communities;
}
