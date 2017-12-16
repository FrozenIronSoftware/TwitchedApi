/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.streamlink;

import com.google.gson.annotations.SerializedName;

public class StreamList {
    @SerializedName("audio_only")
    private Stream audioOnly;
    @SerializedName("160p")
    private Stream _160p;
    @SerializedName("360p")
    private Stream _360p;
    @SerializedName("480p")
    private Stream _480p;
    @SerializedName("720p")
    private Stream _720p;
    @SerializedName("720p90")
    private Stream _720p60;
    @SerializedName("1080p")
    private Stream _1080p;
    @SerializedName("1080p60")
    private Stream _1080p60;
    private Stream worst;
    private Stream best;


    public Stream get160p() {
        return _160p;
    }

    public Stream get360p() {
        return _360p;
    }

    public Stream get480p() {
        return _480p;
    }

    public Stream get720p() {
        return _720p;
    }

    public Stream get720p60() {
        return _720p60;
    }

    public Stream get1080p() {
        return _1080p;
    }

    public Stream get1080p60() {
        return _1080p60;
    }
}
