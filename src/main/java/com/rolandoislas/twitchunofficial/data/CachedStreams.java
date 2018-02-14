package com.rolandoislas.twitchunofficial.data;

import com.rolandoislas.twitchunofficial.util.twitch.helix.Stream;

import java.util.ArrayList;
import java.util.List;

public class CachedStreams {
    private List<String> missingIds;
    private List<String> missingLogins;
    private List<Stream> streams;

    public CachedStreams() {
        missingIds = new ArrayList<>();
        missingLogins = new ArrayList<>();
        streams = new ArrayList<>();
    }

    public List<String> getMissingIds() {
        return missingIds;
    }

    public List<String> getMissingLogins() {
        return missingLogins;
    }

    public List<Stream> getStreams() {
        return streams;
    }
}
