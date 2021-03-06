/*
 * Copyright (c) 2017 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data.model.json.twitch.helix;

import java.util.Comparator;

public class GameViewComparator implements Comparator<Game> {
    @Override
    public int compare(Game a, Game b) {
        return Long.compare(a.getViewersPrimitive(), b.getViewersPrimitive());
    }
}
