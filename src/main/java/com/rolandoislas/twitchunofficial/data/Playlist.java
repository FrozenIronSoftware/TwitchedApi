/*
 * Copyright (c) 2018 Rolando Islas. All Rights Reserved
 *
 */

package com.rolandoislas.twitchunofficial.data;

import com.rolandoislas.twitchunofficial.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * m3u8 playlist lines from a master playlist
 */
public class Playlist {
    private boolean audioOnly;
    private int fps;
    private List<String> lines;
    private int quality;

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
        // Quality
        Pattern qualityPattern = Pattern.compile(".*NAME=\"(\\d+)p?\\d*.*\".*");
        Matcher qualityMatcher = qualityPattern.matcher(lineOne);
        if (qualityMatcher.matches())
            quality = (int) StringUtil.parseLong(qualityMatcher.group(1));
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

    /**
     * Check if the playlist is of the same quality passed or lower
     * @param quality quality string in the format <resolution>p
     * @return is the quality of the stream of equal or lower quality
     */
    public boolean isQualityOrLower(String quality) {
        int qualityInt = (int) StringUtil.parseLong(quality.replace("p", ""));
        return qualityInt > 0 && this.quality <= qualityInt;
    }

    public int getQuality() {
        return quality;
    }
}
