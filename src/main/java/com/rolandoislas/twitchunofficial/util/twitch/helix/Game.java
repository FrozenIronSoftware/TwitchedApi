/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

public class Game {
    private String id;
    private String name;
    @SerializedName("box_art_url")
    private String boxArtUrl;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
