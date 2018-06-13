package com.rolandoislas.twitchunofficial.data.json;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Structure for ad server list environment variable
 */
public class AdServerList {
    @SerializedName("ad_servers")
    private List<AdServer> adServers;

    @Nullable
    public List<AdServer> getAdServers() {
        return adServers;
    }
}
