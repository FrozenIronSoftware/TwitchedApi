/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Stream {
    private String id;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("game_id")
    private String gameId;
    @SerializedName("community_id")
    private List<String> communityIds;
    private String type;
    private String title;
    @SerializedName("viewer_count")
    private long viewerCount;
    @SerializedName("started_at")
    private String startedAt;
    private String language;
    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    // Non-spec fields
    @SerializedName("user_name")
    private String userName;

    // Get/Set

    public String getUserId() {
        return userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
