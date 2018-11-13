package com.frozenironsoftware.twitched.bif.data;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class Download {
    @NotNull private final String url;
    @NotNull private final Path outPath;

    public Download(@NotNull String url, @NotNull Path outPath) {
        this.url = url;
        this.outPath = outPath;
    }

    @NotNull
    public String getUrl() {
        return url;
    }

    @NotNull
    public Path getOutPath() {
        return outPath;
    }
}
