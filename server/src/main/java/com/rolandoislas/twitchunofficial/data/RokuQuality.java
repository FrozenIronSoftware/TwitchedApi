package com.rolandoislas.twitchunofficial.data;

public class RokuQuality {
    private final int maxQuality30;
    private final int maxQuality60;
    private final int maxBitrate;

    public RokuQuality(int maxQuality30, int maxQuality60, int maxBitrate) {
        this.maxQuality30 = maxQuality30;
        this.maxQuality60 = maxQuality60;
        this.maxBitrate = maxBitrate;
    }

    public int getMaxQuality30() {
        return maxQuality30;
    }

    public int getMaxQuality60() {
        return maxQuality60;
    }

    public int getMaxBitrate() {
        return maxBitrate;
    }
}
