package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StreamList {
    @SerializedName(value = "total", alternate = "_total")
    private long total;
    private List<Stream> streams;

    public List<Stream> getStreams() {
        return streams;
    }
}
