/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import com.google.gson.annotations.SerializedName;

public class UserName {
    private String login;
    @SerializedName("display_name")
    private String displayName;

    public UserName(String login, String displayName) {
        this.login = login;
        this.displayName = displayName;
    }

    public UserName() {

    }

    public String getLogin() {
        return login;
    }
}
