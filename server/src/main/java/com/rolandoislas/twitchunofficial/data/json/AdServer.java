package com.rolandoislas.twitchunofficial.data.json;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Structure for an ad server
 */
public class AdServer {
    private String url;
    private List<String> countries;

    @Nullable
    public List<String> getCountries() {
        return countries;
    }

    @Nullable
    public String getUrl() {
        return url;
    }
}
