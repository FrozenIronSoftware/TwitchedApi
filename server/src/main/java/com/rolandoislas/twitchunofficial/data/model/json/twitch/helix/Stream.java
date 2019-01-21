/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data.model.json.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
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

    // VOD fields
    String description;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("published_at")
    private String publishedAt;
    private String url;
    private String viewable;
    @SerializedName("view_count")
    private long viewCount;
    private String duration;

    // Non-spec fields
    @SerializedName("user_name")
    private UserName userName;
    @SerializedName("game_name")
    private String gameName;
    @SerializedName("duration_seconds")
    private long durationSeconds;
    private boolean online = true;
    private boolean encrypted;

    // Get/Set

    public String getUserId() {
        return userId;
    }

    public void setUserName(UserName userName) {
        this.userName = userName;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public void setCommunityIds(ArrayList<String> communityIds) {
        this.communityIds = communityIds;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setViewerCount(long viewerCount) {
        this.viewerCount = viewerCount;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getDuration() {
        return duration;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getId() {
        return id;
    }

    public long getViewerCount() {
        return viewerCount;
    }

    public String getType() {
        return type;
    }

    public UserName getUserName() {
        return userName;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isOnline() {
        return online;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
