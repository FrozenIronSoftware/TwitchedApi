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
    private Stream _360_;
    @SerializedName("480p")
    private Stream _480p;
    @SerializedName("720p")
    private Stream _720p;
    @SerializedName("720p90")
    private Stream _720p60;
    @SerializedName("1080p60")
    private Stream _1080p60;
    private Stream worst;
    private Stream best;
}
