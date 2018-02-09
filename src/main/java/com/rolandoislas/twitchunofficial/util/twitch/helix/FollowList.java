/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Structure for a user follow list
 */
public class FollowList {
    private long total;
    @SerializedName("data")
    private List<Follow> follows;
    private Pagination pagination;
    // Non-spec
    private int rateLimitRemaining;

    public List<Follow> getFollows() {
        return follows;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setFollows(List<Follow> follows) {
        this.follows = follows;
    }

    public void setRateLimitRemaining(int rateLimitRemaining) {
        this.rateLimitRemaining = rateLimitRemaining;
    }

    public int getRateLimitRemaining() {
        return rateLimitRemaining;
    }

    public long getTotal() {
        return total;
    }
}
