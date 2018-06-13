package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

public class Channel {
    @SerializedName(value = "id", alternate = "_id")
    private long id;
    @SerializedName("broadcaster_language")
    private String broadcasterLanguage;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("display_name")
    private String displayName;
    private long followers;
    private String game;
    private String language;
    private String logo;
    private boolean mature;
    private String name;
    private boolean partner;
    @SerializedName("profile_banner")
    private String profileBanner;
    @SerializedName("profile_banner_background_color")
    private String profileBannerBackgroundColor;
    private String status;
    @SerializedName("updated_at")
    private String updatedAt;
    private String url;
    @SerializedName("video_banner")
    private String videoBanner;
    private long views;

    public long getId() {
        return id;
    }

    public String getGame() {
        return game;
    }

    public String getStatus() {
        return status;
    }

    public long getViews() {
        return views;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLanguage() {
        return language;
    }

    public String getVideoBanner() {
        return videoBanner;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }
}
