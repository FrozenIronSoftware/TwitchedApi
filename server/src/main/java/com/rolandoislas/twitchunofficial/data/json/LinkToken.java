/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data.json;

/**
 * Structure for a link token response generated from the link complete page
 */
public class LinkToken {
    private String token;
    private String id;

    public String getToken() {
        return token;
    }

    public String getId() {
        return id;
    }
}
