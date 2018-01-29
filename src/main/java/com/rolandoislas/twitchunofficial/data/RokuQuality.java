package com.rolandoislas.twitchunofficial.data;

public class RokuQuality {
    private final int maxQuality30;
    private final int maxQuality60;

    public RokuQuality(int maxQuality30, int maxQuality60) {
        this.maxQuality30 = maxQuality30;
        this.maxQuality60 = maxQuality60;
    }

    public int getMaxQuality30() {
        return maxQuality30;
    }

    public int getMaxQuality60() {
        return maxQuality60;
    }
}
