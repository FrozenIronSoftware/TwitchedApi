package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.annotations.SerializedName;

public class Community {
    @SerializedName(value = "id", alternate = "_id")
    private String id = "";
    @SerializedName(value = "avatarImageUrl", alternate = "avatar_image_url")
    private String avatarImageUrl = "";
    @SerializedName(value = "coverImageUrl", alternate = "cover_image_url")
    private String coverImageUrl = "";
    private String description = "";
    @SerializedName(value = "descriptionHtml", alternate = "description_html")
    private String descriptionHtml = "";
    private String language = "";
    @SerializedName(value = "ownerId", alternate = "owner_id")
    private String ownerId = "";
    private String rules = "";
    @SerializedName(value = "rulesHtml", alternate = "rules_html")
    private String rulesHtml = "";
    private String summary = "";
    private long channels;
    private String name = "";
    private long viewers;

    // Non-spec
    private long modified;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public long getModified() {
        return modified;
    }

    public String getAvatarImageUrl() {
        return avatarImageUrl;
    }

    public void setAvatarImageUrl(String avatarImageUrl) {
        this.avatarImageUrl = avatarImageUrl;
    }
}
