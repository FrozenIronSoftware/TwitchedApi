package com.rolandoislas.twitchunofficial.util.twitch.helix;

import java.util.List;

public class StreamUtil {
    /**
     * Check if a list of streams contains at least one stream with specified user id
     * @param streams list of streams
     * @param id user id to check against
     * @return list contains id
     */
    public static boolean streamListContainsId(List<Stream> streams, String id) {
        for (Stream stream : streams)
            if (stream != null && id != null && stream.getUserId() != null && stream.getUserId().equals(id))
                return true;
        return false;
    }

    /**
     * Check if a list of streams contains at least one streams with the specified user login
     * @param streams list of streams
     * @param login user login to check against
     * @return does the list contain the user login
     */
    public static boolean streamListContainsLogin(List<Stream> streams, String login) {
        for (Stream stream : streams)
            if (stream != null && login != null && stream.getUserName() != null &&
                    stream.getUserName().getLogin() != null && stream.getUserName().getLogin().equals(login))
                return true;
        return false;
    }
}
