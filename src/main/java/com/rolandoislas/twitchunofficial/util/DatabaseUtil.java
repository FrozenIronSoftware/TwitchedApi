package com.rolandoislas.twitchunofficial.util;

import com.rolandoislas.twitchunofficial.util.twitch.kraken.Community;
import org.jetbrains.annotations.Nullable;
import org.sql2o.Connection;
import org.sql2o.Query;
import org.sql2o.Sql2o;
import org.sql2o.Sql2oException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseUtil {
    private static Sql2o sql2o;
    private static int connections;
    private static int MAX_CONNECTIONS = 5;

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
        String mysqlUrl = mysqlMatches.group(1) + mysqlMatches.group(4) + "?useSSL=false";
        String mysqlUsername = mysqlMatches.group(2);
        String mysqlPassword = mysqlMatches.group(3);
        return new Sql2o(mysqlUrl, mysqlUsername, mysqlPassword);
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
        String sqlIds = "select twitch_community_id from followed_communities where twitch_user_id = :twitch_user_id" +
                " and %s and following = true limit :limit offset :offset;";
        sqlIds = String.format(sqlIds, toIdSql);
        String communitySql = "community_id = %s";
        StringBuilder sqlCommunities = new StringBuilder("select * from cached_communities where");
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
            for (int communityIdIndex = 0; communityIdIndex < communityIds.size(); communityIdIndex++) {
                sqlCommunities.append(communityIdIndex == 0 ? " " : " or ")
                        .append(String.format(communitySql, ":p" + (communityIdIndex + 1)));
            }
            List<Community> communities = connection.createQuery(sqlCommunities.toString(), false)
                    .withParams(communityIds.toArray())
                    .addColumnMapping("community_id", "id")
                    .addColumnMapping("cover_image_url", "coverImageUrl")
                    .executeAndFetch(Community.class);
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
        String sqlSelect = "select twitch_user_id from followed_communities where twitch_user_id = :twitch_user_id" +
                " and twitch_community_id = :twitch_community_id;";
        String sqlUpdate = "update followed_communities set following = :following where" +
                " twitch_user_id = :twitch_user_id and twitch_community_id = :twitch_community_id;";
        String sqlInsert = "insert into followed_communities (twitch_user_id, twitch_community_id, following)" +
                " values (:twitch_user_id, :twitch_community_id, :following);";
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
        String sqlSelect = "select community_id from cached_communities where community_id = :community_id;";
        String sqlUpdate = "update cached_communities set name = :name, description = :description," +
                " cover_image_url = :cover_image_url, modified = :modified where community_id = :community_id;";
        String sqlInsert = "insert into cached_communities (community_id, name, description, cover_image_url," +
                " modified) values (:community_id, :name, :description, :cover_image_url, :modified);";
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
                        .addParameter("description", community.getDescription())
                        .addParameter("cover_image_url", community.getCoverImageUrl())
                        .addParameter("community_id", community.getId())
                        .addParameter("modified", System.currentTimeMillis())
                        .executeUpdate();
            }
            // Insert
            else {
                connection.createQuery(sqlInsert, false)
                        .addParameter("name", community.getName())
                        .addParameter("description", community.getDescription())
                        .addParameter("cover_image_url", community.getCoverImageUrl())
                        .addParameter("community_id", community.getId())
                        .addParameter("modified", System.currentTimeMillis())
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
        String sql = "select * from cached_communities where community_id = :community_id";
        Connection connection = null;
        try {
            connection = getConnection();
            List<Community> communities = connection.createQuery(sql, false)
                    .addParameter("community_id", id)
                    .addColumnMapping("community_id", "id")
                    .addColumnMapping("cover_image_url", "coverImageUrl")
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
}
