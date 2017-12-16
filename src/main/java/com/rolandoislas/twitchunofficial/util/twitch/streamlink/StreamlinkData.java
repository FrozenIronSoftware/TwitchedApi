/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.streamlink;

import com.rolandoislas.twitchunofficial.util.twitch.helix.User;

public class StreamlinkData {
    private StreamList streams;
    private String plugin;
    // Non-spec
    private User user;

    public void setUser(User user) {
        this.user = user;
    }

    public StreamList getStreams() {
        return streams;
    }
}
