/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

/**
 * Data structure for a user follow (to or from)
 */
public class Follow {
    @SerializedName("from_id")
    private String fromId;
    @SerializedName("to_id")
    private String toId;
    @SerializedName("followed_at")
    private String followedAt;

    public String getToId() {
        return toId;
    }

    public void setToId(String toId) {
        this.toId = toId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
    }
}
