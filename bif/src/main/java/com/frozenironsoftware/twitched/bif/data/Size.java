package com.frozenironsoftware.twitched.bif.data;

public class Size {
    private final int width;
    private final int height;
    private final String name;

    public Size(int width, int height, String name) {
        this.width = width;
        this.height = height;
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }
}
