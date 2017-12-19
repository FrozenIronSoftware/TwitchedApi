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

    public List<Follow> getFollows() {
        return follows;
    }
}
