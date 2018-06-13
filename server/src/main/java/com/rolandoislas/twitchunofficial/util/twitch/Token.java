/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch;

import com.google.gson.annotations.SerializedName;

/**
 * Structure for a channel token returned for HLS data
 */
public class Token {
    private String token;
    private String sig;
    @SerializedName("mobile_restricted")
    private boolean mobileRestricted;

    public String getToken() {
        return token;
    }

    public String getSig() {
        return sig;
    }

    public enum TYPE {VOD, CHANNEL}
}
