package com.rolandoislas.twitchunofficial.data.json;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Class representing a link id and its version
 */
public class LinkId {
    @SerializedName("id")
    private final String linkId;
    private final int version;

    /**
     * Construct a link id from an id string and version number
     * @param linkId id
     * @param version version
     */
    public LinkId(String linkId, int version) {
        this.linkId = linkId;
        this.version = version;
    }

    /**
     * Construct a link id from a GSON serialized JSON string
     * @param linkIdJson GSON JSON string
     * @param gson GSON instance
     */
    public LinkId(@Nullable String linkIdJson, Gson gson) {
        if (linkIdJson == null) {
            this.linkId = null;
            this.version = 0;
            return;
        }
        String linkIdString;
        int version;
        LinkId linkId = null;
        try {
            linkId = gson.fromJson(linkIdJson, LinkId.class);
        }
        catch (JsonSyntaxException ignore) {}
        if (linkId == null || linkId.getLinkId() == null || linkId.getVersion() < 1) {
            linkIdString = null;
            version = 0;
        }
        else {
            linkIdString = linkId.getLinkId();
            version = linkId.getVersion();
        }
        this.linkId = linkIdString;
        this.version = version;
    }

    @Nullable
    public String getLinkId() {
        return linkId;
    }

    public int getVersion() {
        return version;
    }
}
