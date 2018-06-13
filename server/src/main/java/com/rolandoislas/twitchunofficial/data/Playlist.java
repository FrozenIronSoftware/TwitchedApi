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
    private int bitrate;

    public Playlist(String lineOne, String lineTwo, String lineThree) {
        // Set closed caption to none
        lineTwo += ",CLOSED-CAPTIONS=NONE";
        // Add lines
        lines = new ArrayList<>();
        lines.add(lineOne);
        lines.add(lineTwo);
        lines.add(lineThree);
        audioOnly = lineOne.contains("audio") || lineTwo.contains("audio");
        // Quality
        Pattern qualityPattern = Pattern.compile(".*NAME=\"(\\d+)p?(\\d*).*\".*");
        Matcher qualityMatcher = qualityPattern.matcher(lineOne);
        if (qualityMatcher.matches()) {
            quality = (int) StringUtil.parseLong(qualityMatcher.group(1));
            fps = (int) StringUtil.parseLong(qualityMatcher.group(2));
            if (fps == 0)
                fps = 30;
        }
        // Bitrate
        Pattern bitratePattern = Pattern.compile(".*BANDWIDTH=(\\d+),.*");
        Matcher bitrateMatcher = bitratePattern.matcher(lineTwo);
        if (bitrateMatcher.matches())
            bitrate = (int) StringUtil.parseLong(bitrateMatcher.group(1));
    }

    public Playlist(int quality, int fps) {
        this.quality = quality;
        this.fps = fps;
        this.audioOnly = false;
        this.lines = new ArrayList<>();
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
    @SuppressWarnings("WeakerAccess")
    public boolean isQualityOrLower(String quality) {
        int qualityInt = (int) StringUtil.parseLong(quality.replace("p", ""));
        return qualityInt > 0 && this.quality <= qualityInt;
    }

    public int getQuality() {
        return quality;
    }

    /**
     * @see Playlist#isQualityOrLower(String)
     * @param quality quality to check for or below
     * @return is quality lower or equal
     */
    public boolean isQualityOrLower(int quality) {
        return quality > 0 && this.quality <= quality;
    }

    public int getBitrate() {
        return bitrate;
    }
}
