/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

public class User {
    private String id;
    private String login;
    @SerializedName("display_name")
    private String displayName;
    private String type;
    @SerializedName("broadcaster_type")
    private String broadcasterType;
    private String description;
    @SerializedName("profile_image_url")
    private String profileImageUrl;
    @SerializedName("offline_image_url")
    private String offlineImageUrl;
    @SerializedName("view_count")
    private long viewCount;
    private String email;

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLogin() {
        return login;
    }
}
