/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data.model.json.twitch.helix;

import java.util.Comparator;

public class StreamViewComparator implements Comparator<Stream> {
    @Override
    public int compare(Stream a, Stream b) {
        long viewA = a.getType().equalsIgnoreCase("user") ||
                a.getType().equalsIgnoreCase("user_follow") ? -1 : a.getViewerCount();
        long viewB = b.getType().equalsIgnoreCase("user") ||
                b.getType().equalsIgnoreCase("user_follow") ? -1 : b.getViewerCount();
        return Long.compare(viewA, viewB);
    }
}
