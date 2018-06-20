package com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChannelList {
    @SerializedName(value = "total", alternate = "_total")
    private long total;
    private List<Channel> channels;

    public List<Channel> getChannels() {
        return channels;
    }
}
