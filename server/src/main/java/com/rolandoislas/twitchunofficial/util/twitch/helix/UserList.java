/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UserList {
    @SerializedName("data")
    private List<User> users;

    public List<User> getUsers() {
        return users;
    }
}
