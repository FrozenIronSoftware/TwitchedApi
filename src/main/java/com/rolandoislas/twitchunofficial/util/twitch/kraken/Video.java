/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;
import me.philippheuer.twitch4j.model.Channel;
import me.philippheuer.twitch4j.model.VideoFramerates;
import me.philippheuer.twitch4j.model.VideoResolutions;

import java.util.Date;
import java.util.Map;

public class Video {
    @SerializedName("_id")
    private long id;

    private String title;

    private String description;

    @SerializedName("description_html")
    private String descriptionHtml;

    @SerializedName("broadcast_id")
    private long broadcastId;

    @SerializedName("broadcast_type")
    private String broadcastType;

    private Channel channel;

    private String status;

    private String tagList;

    private String game;

    private long length;

    private Map<String, String> preview;

    private String url;

    private long views;

    private VideoFramerates fps;

    private VideoResolutions resolutions;

    @SerializedName("created_at")
    private Date createdAt;

    @SerializedName("published_at")
    private Date publishedAt;

    public String getGame() {
        return game;
    }
}
