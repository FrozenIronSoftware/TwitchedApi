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

    // Non-spec
    private boolean exists = true;

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLogin() {
        return login;
    }

    public String getOfflineImageUrl() {
        return offlineImageUrl;
    }

    public long getViewCount() {
        return viewCount;
    }

    public String getDescription() {
        return description;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBroadcasterType() {
        return broadcasterType;
    }

    public void setBroadcasterType(String broadcasterType) {
        this.broadcasterType = broadcasterType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setOfflineImageUrl(String offlineImageUrl) {
        this.offlineImageUrl = offlineImageUrl;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Verify that the data required for user operations is present
     * @return true if id and login are not null or empty strings
     */
    public boolean verifyData() {
        return getId() != null && !getId().isEmpty() && getLogin() != null && !getLogin().isEmpty();
    }
}
