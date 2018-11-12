package com.frozenironsoftware.twitched.bif.data;

import java.nio.file.Path;
import java.util.List;

public class Frames {
    private final List<Path> fhdFrames;
    private final List<Path> hdFrames;
    private final List<Path> sdFrames;

    public Frames(List<Path> fhdFrames, List<Path> hdFrames, List<Path> sdFrames) {
        this.fhdFrames = fhdFrames;
        this.hdFrames = hdFrames;
        this.sdFrames = sdFrames;
    }

    public List<Path> getFhdFrames() {
        return fhdFrames;
    }

    public List<Path> getHdFrames() {
        return hdFrames;
    }

    public List<Path> getSdFrames() {
        return sdFrames;
    }
}
