/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import java.util.Comparator;

public class StreamViewComparator implements Comparator<Stream> {
    @Override
    public int compare(Stream a, Stream b) {
        long viewA = a.getType().equalsIgnoreCase("user") ? -1 : a.getViewerCount();
        long viewB = b.getType().equalsIgnoreCase("user") ? -1 : b.getViewerCount();
        return Long.compare(viewA, viewB);
    }
}
