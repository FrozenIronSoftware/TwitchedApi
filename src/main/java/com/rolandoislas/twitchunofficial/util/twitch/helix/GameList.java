/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GameList {
    @SerializedName("data")
    private List<Game> games;

    public List<Game> getGames() {
        return games;
    }
}
