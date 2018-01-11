/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.util.twitch.helix;

import java.util.Comparator;

public class StreamViewComparator implements Comparator<Stream> {
    @Override
    public int compare(Stream a, Stream b) {
        return Long.compare(a.getViewerCount(), b.getViewerCount());
    }
}
