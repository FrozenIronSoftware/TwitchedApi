/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data;

import java.util.ArrayList;
import java.util.List;

/**
 * m3u8 playlist lines from a master playlist
 */
public class Playlist {
    private boolean audioOnly;
    private int fps;
    private List<String> lines;

    public Playlist(String lineOne, String lineTwo, String lineThree) {
        lines = new ArrayList<>();
        lines.add(lineOne);
        lines.add(lineTwo);
        lines.add(lineThree);
        if (lineOne.contains("p60") || lineTwo.contains("p60"))
            fps = 60;
        else
            fps = 30;
        audioOnly = lineOne.contains("audio") || lineTwo.contains("audio");
    }

    public int getFps() {
        return fps;
    }

    public List<String> getLines() {
        return lines;
    }

    public boolean isVideo() {
        return !audioOnly;
    }
}
