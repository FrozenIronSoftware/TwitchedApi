package com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken;

import com.google.gson.annotations.SerializedName;

public class Stream {
    @SerializedName(value = "id", alternate = "_id")
    private long id;
    @SerializedName("average_fps")
    private float averageFps;
    private Channel channel;
    @SerializedName("created_at")
    private String createdAt;
    private int delay;
    private String game;
    @SerializedName("is_playlist")
    private boolean isPlaylist;
    private Preview preview;
    @SerializedName("video_height")
    private int videoHeight;
    private long viewers;

    public Channel getChannel() {
        return channel;
    }
}
