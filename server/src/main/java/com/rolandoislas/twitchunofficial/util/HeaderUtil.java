package com.rolandoislas.twitchunofficial.util;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import spark.Request;

public class HeaderUtil {
    /**
     * Extract X-Twitched-Version header if present and wrap it in a ComparableVersion
     * @param request spark request
     * @return ComparableVersion - 1.0 if no header is present
     */
    @NotNull
    public static ComparableVersion extractVersion(Request request) {
        String versionString = request.headers("X-Twitched-Version");
        return new ComparableVersion(versionString == null ? "1.0" : versionString);
    }
}
