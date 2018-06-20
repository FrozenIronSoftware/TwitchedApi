/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data.model.json.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StreamList {
    @SerializedName("data")
    private List<Stream> streams;
    private Pagination pagination;

    public List<Stream> getStreams() {
        return streams;
    }

    public Pagination getPagination() {
        return pagination;
    }
}
