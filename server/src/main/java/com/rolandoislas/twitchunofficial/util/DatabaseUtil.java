package com.rolandoislas.twitchunofficial.util;

import com.rolandoislas.twitchunofficial.data.model.StreamQuality;
import com.rolandoislas.twitchunofficial.data.model.UserDatabaseCredentials;
import com.rolandoislas.twitchunofficial.data.model.json.twitch.kraken.Community;
import org.jetbrains.annotations.Nullable;
import org.sql2o.Connection;
import org.sql2o.Query;
import org.sql2o.Sql2o;
import org.sql2o.Sql2oException;
import org.sql2o.quirks.PostgresQuirks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseUtil {
    private static Sql2o sql2o;
    private static int connections;
    private static int MAX_CONNECTIONS =
            (int) StringUtil.parseLong(System.getenv().getOrDefault("SQL_CONNECTIONS", "5"));
    private static String schema;

    /**
     * Get new sql instance with a parsed connection url
     * Use sql2o instance instead of calling this multiple times
     * @param serverUrl connection url
     * @return new instance
     */
    private static Sql2o getSql2oInstance(String serverUrl) {
        Pattern mysqlPattern = Pattern.compile("(.*://)(.*):(.*)@(.*)"); // (scheme)(user):(pass)@(url)
        Matcher mysqlMatches = mysqlPattern.matcher(serverUrl);
        if (!mysqlMatches.find()) {
            Logger.warn("Could not parse mysql database connection string.");
            System.exit(1);
        }
        String mysqlUrl = mysqlMatches.group(1).replace("postgres", "postgresql") +
                mysqlMatches.group(4);
        if (Boolean.parseBoolean(System.getenv().getOrDefault("SQL_SSL", "true")))
            mysqlUrl += "?sslmode=require";
        String mysqlUsername = mysqlMatches.group(2);
        String mysqlPassword = mysqlMatches.group(3);
        schema = System.getenv().getOrDefault("SQL_SCHEMA", "twitched");
        return new Sql2o(mysqlUrl, mysqlUsername, mysqlPassword, new PostgresQuirks());
    }

    /**
     * Set the server url an initialize a new sql2o instance
     * @param serverUrl url of sql server
     */
    public static void setServer(String serverUrl) {
        sql2o = getSql2oInstance(serverUrl);
    }

    /**
     * Release a connection
     * @param connection sql2o connection
     */
    private static void releaseConnection(@Nullable Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            }
            catch (Sql2oException e) {
                Logger.exception(e);
            }
        }
        if (connections > 0)
            connections--;
    }

    /**
     * Try to fetch a connection
     * @return sql2o connection
     */
    private static Connection getConnection() {
        while (connections >= MAX_CONNECTIONS)
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}
        Connection connection = sql2o.open();
        connections++;
        return connection;
    }

    /**
     * Try to fetch a transaction
     * @return sql2o connection
     */
    private static Connection getTransaction() {
        while (connections >= MAX_CONNECTIONS)
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}
        Connection connection = sql2o.beginTransaction();
        connections++;
        return connection;
    }

    /**
     * Get followed communities from the SQL database
     * @param userId user id to get follows for
     * @param limit limit
     * @param offset page offset
     * @param toId id to limit to
     * @return list of community ids or null on error
     */
    @Nullable
    public static List<Community> getUserFollowedCommunities(String userId, int limit, int offset,
                                                          @Nullable String toId) {
        String toIdSql = "twitch_community_id = :twitch_community_id";
        if (toId == null || toId.isEmpty())
            toIdSql = "true";
        String sqlIds = "select twitch_community_id from %s.followed_communities where" +
                " twitch_user_id = :twitch_user_id and %s and following = true limit :limit offset :offset;";
        sqlIds = String.format(sqlIds, schema, toIdSql);
        String communitySql = "community_id = %s";
        StringBuilder sqlCommunities = new StringBuilder(
                String.format("select * from %s.cached_communities where", schema));
        Connection connection = null;
        try {
            connection = getTransaction();
            // Get followed ids
            Query query = connection.createQuery(sqlIds, false)
                    .addParameter("twitch_user_id", userId)
                    .addParameter("limit", limit)
                    .addParameter("offset", offset);
            if (toId != null && !toId.isEmpty())
                    query.addParameter("twitch_community_id", toId);
            List<String> communityIds = query.executeAndFetch(String.class);
            if (communityIds == null) {
                connection.commit();
                releaseConnection(connection);
                return null;
            }
            // Get cached communities
            List<Community> communities = new ArrayList<>();
            if (communityIds.size() > 0) {
                for (int communityIdIndex = 0; communityIdIndex < communityIds.size(); communityIdIndex++) {
                    sqlCommunities.append(communityIdIndex == 0 ? " " : " or ")
                            .append(String.format(communitySql, ":p" + (communityIdIndex + 1)));
                }
                communities = connection.createQuery(sqlCommunities.toString(), false)
                        .withParams(communityIds.toArray())
                        .addColumnMapping("community_id", "id")
                        .addColumnMapping("avatar_image_url", "avatarImageUrl")
                        .addColumnMapping("display_name", "displayName")
                        .executeAndFetch(Community.class);
            }
            // Clean up
            connection.commit();
            releaseConnection(connection);
            return communities;
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return null;
        }
    }

    /**
     * Set or delete user community follow
     * @param id user id
     * @param communityId community id
     * @param setFollowing should set follow
     * @return success of set/delete
     */
    public static boolean setUserFollowCommunity(String id, String communityId, boolean setFollowing) {
        String sqlSelect = "select twitch_user_id from %s.followed_communities where twitch_user_id = :twitch_user_id" +
                " and twitch_community_id = :twitch_community_id;";
        sqlSelect = String.format(sqlSelect, schema);
        String sqlUpdate = "update %s.followed_communities set following = :following where" +
                " twitch_user_id = :twitch_user_id and twitch_community_id = :twitch_community_id;";
        sqlUpdate = String.format(sqlUpdate, schema);
        String sqlInsert = "insert into %s.followed_communities (twitch_user_id, twitch_community_id, following)" +
                " values (:twitch_user_id, :twitch_community_id, :following);";
        sqlInsert = String.format(sqlInsert, schema);
        Connection connection = null;
        try {
            connection = getTransaction();
            List<String> communities = connection.createQuery(sqlSelect, false)
                    .addParameter("twitch_user_id", id)
                    .addParameter("twitch_community_id", communityId)
                    .executeAndFetch(String.class);
            // Update
            if (communities.size() == 1) {
                connection.createQuery(sqlUpdate, false)
                        .addParameter("twitch_user_id", id)
                        .addParameter("twitch_community_id", communityId)
                        .addParameter("following", setFollowing)
                        .executeUpdate();
            }
            // Insert
            else {
                connection.createQuery(sqlInsert, false)
                        .addParameter("twitch_user_id", id)
                        .addParameter("twitch_community_id", communityId)
                        .addParameter("following", setFollowing)
                        .executeUpdate();
            }
            connection.commit();
            releaseConnection(connection);
            return true;
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return false;
        }
    }

    /**
     * Add or update a community
     * @param community community
     */
    public static boolean cacheCommunity(Community community) {
        String sqlSelect = "select community_id from %s.cached_communities where community_id = :community_id;";
        sqlSelect = String.format(sqlSelect, schema);
        String sqlUpdate = "update %s.cached_communities set name = :name, summary = :summary," +
                " avatar_image_url = :avatar_image_url, modified = :modified, display_name = :display_name" +
                " where community_id = :community_id;";
        sqlUpdate = String.format(sqlUpdate, schema);
        String sqlInsert = "insert into %s.cached_communities (community_id, name, summary, avatar_image_url," +
                " modified, display_name) values (:community_id, :name, :summary, :avatar_image_url, :modified," +
                " :display_name);";
        sqlInsert = String.format(sqlInsert, schema);
        Connection connection = null;
        try {
            connection = getTransaction();
            List<String> communities = connection.createQuery(sqlSelect, false)
                    .addParameter("community_id", community.getId())
                    .executeAndFetch(String.class);
            // Update
            if (communities.size() == 1) {
                connection.createQuery(sqlUpdate, false)
                        .addParameter("name", community.getName())
                        .addParameter("summary", community.getSummary())
                        .addParameter("avatar_image_url", community.getAvatarImageUrl())
                        .addParameter("community_id", community.getId())
                        .addParameter("modified", System.currentTimeMillis())
                        .addParameter("display_name", community.getDisplayName())
                        .executeUpdate();
            }
            // Insert
            else {
                connection.createQuery(sqlInsert, false)
                        .addParameter("name", community.getName())
                        .addParameter("summary", community.getSummary())
                        .addParameter("avatar_image_url", community.getAvatarImageUrl())
                        .addParameter("community_id", community.getId())
                        .addParameter("modified", System.currentTimeMillis())
                        .addParameter("display_name", community.getDisplayName())
                        .executeUpdate();
            }
            connection.commit();
            releaseConnection(connection);
            return true;
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return false;
        }
    }

    /**
     * Try to fetch a cached community from the database
     * @param id twitch community id
     * @return community
     */
    @Nullable
    public static Community getCommunity(String id) {
        String sql = "select * from %s.cached_communities where community_id = :community_id";
        sql = String.format(sql, schema);
        Connection connection = null;
        try {
            connection = getConnection();
            List<Community> communities = connection.createQuery(sql, false)
                    .addParameter("community_id", id)
                    .addColumnMapping("community_id", "id")
                    .addColumnMapping("avatar_image_url", "avatarImageUrl")
                    .addColumnMapping("display_name", "displayName")
                    .executeAndFetch(Community.class);
            releaseConnection(connection);
            if (communities.size() == 1)
                return communities.get(0);
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return null;
        }
        return null;
    }

    /**
     * Get stored admin user credentials for a username
     * @param username user to fetch data for
     * @return user data or null if not found
     */
    @Nullable
    public static UserDatabaseCredentials getAdminUserData(String username) {
        String sql = "select * from %s.admin_users where username = :username";
        sql = String.format(sql, schema);
        Connection connection = null;
        try {
            connection = getConnection();
            List<UserDatabaseCredentials> userDatabaseCredentials = connection.createQuery(sql, false)
                    .addParameter("username", username)
                    .executeAndFetch(UserDatabaseCredentials.class);
            releaseConnection(connection);
            if (userDatabaseCredentials.size() == 1)
                return userDatabaseCredentials.get(0);
            return null;
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return null;
        }
    }

    /**
     * Get stream qualities
     * @return list of stream qualities or null on error
     */
    @Nullable
    public static List<StreamQuality> getStreamQualities() {
        String sql = "select * from %s.stream_qualities";
        sql = String.format(sql, schema);
        Connection connection = null;
        try {
            connection = getConnection();
            List<StreamQuality> streamQualities = connection.createQuery(sql, false)
                    .addColumnMapping("240p30", "_240p30")
                    .addColumnMapping("240p60", "_240p60")
                    .addColumnMapping("480p30", "_480p30")
                    .addColumnMapping("480p60", "_480p60")
                    .addColumnMapping("720p30", "_720p30")
                    .addColumnMapping("720p60", "_720p60")
                    .addColumnMapping("1080p30", "_1080p30")
                    .addColumnMapping("1080p60", "_1080p60")
                    .addColumnMapping("only_source_60", "onlySource60")
                    .executeAndFetch(StreamQuality.class);
            releaseConnection(connection);
            return streamQualities;
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
            return null;
        }
    }

    /**
     * Set stream qualities
     * @param streamQualities stream qualities
     */
    public static void setStreamQualities(List<StreamQuality> streamQualities) throws IllegalArgumentException {
        String sqlSelect = "select * from %s.stream_qualities;";
        sqlSelect = String.format(sqlSelect, schema);
        String sqlInsert = "insert into %s.stream_qualities (model, bitrate, \"240p30\", \"240p60\", \"480p30\"," +
                " \"480p60\", \"720p30\", \"720p60\", \"1080p30\", \"1080p60\", only_source_60, comment) values" +
                " (:model, :bitrate, :_240_30," +
                " :_240_60, :_480_30, :_480_60, :_720_30, :_720_60, :_1080_30, :_1080_60, :only_source_60, :comment);";
        sqlInsert = String.format(sqlInsert, schema);
        String sqlUpdate = "update %s.stream_qualities set bitrate = :bitrate, \"240p30\" = :_240_30," +
                " \"240p60\" = :_240_60, \"480p30\" = :_480_30, \"480p60\" = :_480_60, \"720p30\" = :_720_30," +
                " \"720p60\" = :_720_60, \"1080p30\" = :_1080_30, \"1080p60\" = :_1080_60," +
                " only_source_60 = :only_source_60, comment = :comment where model = :model;";
        sqlUpdate = String.format(sqlUpdate, schema);
        String sqlRemoveNot = "model <> %s";
        String sqlRemove = "delete from %s.stream_qualities where";
        sqlRemove = String.format(sqlRemove, schema);
        Connection connection = null;
        try {
            connection = getTransaction();
            // Get all qualities
            List<StreamQuality> existingQualities = connection.createQuery(sqlSelect, false)
                    .addColumnMapping("240p30", "_240p30")
                    .addColumnMapping("240p60", "_240p60")
                    .addColumnMapping("480p30", "_480p30")
                    .addColumnMapping("480p60", "_480p60")
                    .addColumnMapping("720p30", "_720p30")
                    .addColumnMapping("720p60", "_720p60")
                    .addColumnMapping("1080p30", "_1080p30")
                    .addColumnMapping("1080p60", "_1080p60")
                    .addColumnMapping("only_source_60", "onlySource60")
                    .executeAndFetch(StreamQuality.class);
            // Remove
            List<String> modelsToNotRemove = new ArrayList<>();
            StringBuilder sqlRemoveBuilder = new StringBuilder(sqlRemove);
            int streamQualityIndex = 1;
            for (StreamQuality streamQuality : streamQualities) {
                modelsToNotRemove.add(streamQuality.getModel());
                if (streamQualityIndex == 1)
                    sqlRemoveBuilder.append(" ");
                else
                    sqlRemoveBuilder.append(" and ");
                sqlRemoveBuilder
                        .append(String.format(sqlRemoveNot, ":p"))
                        .append(String.valueOf(streamQualityIndex));
                streamQualityIndex++;
            }
            sqlRemoveBuilder.append(";");
            connection.createQuery(sqlRemoveBuilder.toString(), false)
                    .withParams(modelsToNotRemove.toArray())
                    .executeUpdate();
            // Update/Insert qualities
            for (StreamQuality streamQuality : streamQualities) {
                if (!streamQuality.validate())
                    throw new IllegalArgumentException("Stream quality did not validate: Name: " +
                            String.valueOf(streamQuality.getModel()));
                boolean exists = false;
                for (StreamQuality existingQuality : existingQualities) {
                    if (existingQuality.getModel().equals(streamQuality.getModel())) {
                        exists = true;
                        break;
                    }
                }
                // Update
                if (exists) {
                    connection.createQuery(sqlUpdate, false)
                            .addParameter("model", streamQuality.getModel())
                            .addParameter("bitrate", streamQuality.getBitrate())
                            .addParameter("_240_30", streamQuality.get240p30())
                            .addParameter("_240_60", streamQuality.get240p60())
                            .addParameter("_480_30", streamQuality.get480p30())
                            .addParameter("_480_60", streamQuality.get480p60())
                            .addParameter("_720_30", streamQuality.get720p30())
                            .addParameter("_720_60", streamQuality.get720p60())
                            .addParameter("_1080_30", streamQuality.get1080p30())
                            .addParameter("_1080_60", streamQuality.get1080p60())
                            .addParameter("only_source_60", streamQuality.getOnlySource60())
                            .addParameter("comment", streamQuality.getComment())
                            .executeUpdate();
                }
                // Insert
                else {
                    connection.createQuery(sqlInsert, false)
                            .addParameter("model", streamQuality.getModel())
                            .addParameter("bitrate", streamQuality.getBitrate())
                            .addParameter("_240_30", streamQuality.get240p30())
                            .addParameter("_240_60", streamQuality.get240p60())
                            .addParameter("_480_30", streamQuality.get480p30())
                            .addParameter("_480_60", streamQuality.get480p60())
                            .addParameter("_720_30", streamQuality.get720p30())
                            .addParameter("_720_60", streamQuality.get720p60())
                            .addParameter("_1080_30", streamQuality.get1080p30())
                            .addParameter("_1080_60", streamQuality.get1080p60())
                            .addParameter("only_source_60", streamQuality.getOnlySource60())
                            .addParameter("comment", streamQuality.getComment())
                            .executeUpdate();
                }
            }
            connection.commit();
            releaseConnection(connection);
        }
        catch (Sql2oException e) {
            Logger.exception(e);
            releaseConnection(connection);
        }
    }
}
