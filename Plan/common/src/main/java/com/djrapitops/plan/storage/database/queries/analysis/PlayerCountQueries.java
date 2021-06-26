/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.storage.database.queries.analysis;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.building.Sql;
import com.djrapitops.plan.storage.database.sql.tables.SessionsTable;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Queries for server overview tab data.
 *
 * @author AuroraLS3
 */
public class PlayerCountQueries {

    private PlayerCountQueries() {
        // Static method class
    }

    private static QueryStatement<Integer> queryPlayerCount(String sql, long after, long before, ServerUUID serverUUID) {
        return new QueryStatement<Integer>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, before);
                statement.setLong(2, after);
                statement.setString(3, serverUUID.toString());
            }

            @Override
            public Integer processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getInt("player_count") : 0;
            }
        };
    }

    private static QueryStatement<Integer> queryPlayerCount(String sql, long after, long before) {
        return new QueryStatement<Integer>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, before);
                statement.setLong(2, after);
            }

            @Override
            public Integer processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getInt("player_count") : 0;
            }
        };
    }

    public static Query<Integer> uniquePlayerCount(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + "<=?" +
                AND + SessionsTable.SESSION_START + ">=?" +
                AND + SessionsTable.SERVER_UUID + "=?";

        return queryPlayerCount(sql, after, before, serverUUID);
    }

    /**
     * Fetch uniquePlayer count for ALL servers.
     *
     * @param after  After epoch ms
     * @param before Before epoch ms
     * @return Unique player count (players who played within time frame)
     */
    public static Query<Integer> uniquePlayerCount(long after, long before) {
        String sql = SELECT + "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + "<=?" +
                AND + SessionsTable.SESSION_START + ">=?";

        return queryPlayerCount(sql, after, before);
    }

    public static Query<Map<ServerUUID, Integer>> uniquePlayerCounts(long after, long before) {
        String sql = SELECT + SessionsTable.SERVER_UUID + ",COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + "<=?" +
                AND + SessionsTable.SESSION_START + ">=?" +
                GROUP_BY + UserInfoTable.SERVER_UUID;

        return new QueryStatement<Map<ServerUUID, Integer>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, before);
                statement.setLong(2, after);
            }

            @Override
            public Map<ServerUUID, Integer> processResults(ResultSet set) throws SQLException {
                Map<ServerUUID, Integer> byServer = new HashMap<>();
                while (set.next()) {
                    byServer.put(ServerUUID.fromString(set.getString(UserInfoTable.SERVER_UUID)), set.getInt("player_count"));
                }
                return byServer;
            }
        };
    }

    /**
     * Fetch a EpochMs - Count map of unique players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @param serverUUID     UUID of the Plan server
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many unique players played that day
     */
    public static Query<NavigableMap<Long, Integer>> uniquePlayerCounts(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectUniquePlayersPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectUniquePlayersPerDay, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> uniquePerDay = new TreeMap<>();
                    while (set.next()) {
                        uniquePerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of unique players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @param serverUUID     UUID of the Plan server
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many unique players played that day
     */
    public static Query<NavigableMap<Long, Integer>> hourlyUniquePlayerCounts(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectUniquePlayersPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToHourStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectUniquePlayersPerDay, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> uniquePerDay = new TreeMap<>();
                    while (set.next()) {
                        uniquePerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of unique players on ALL servers.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many unique players played that day
     */
    public static Query<NavigableMap<Long, Integer>> uniquePlayerCounts(long after, long before, long timeZoneOffset) {
        return database -> {
            Sql sql = database.getSql();
            String selectUniquePlayersPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectUniquePlayersPerDay, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> uniquePerDay = new TreeMap<>();
                    while (set.next()) {
                        uniquePerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of unique players on ALL servers.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many unique players played that day
     */
    public static Query<NavigableMap<Long, Integer>> hourlyUniquePlayerCounts(long after, long before, long timeZoneOffset) {
        return database -> {
            Sql sql = database.getSql();
            String selectUniquePlayersPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToHourStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectUniquePlayersPerDay, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> uniquePerDay = new TreeMap<>();
                    while (set.next()) {
                        uniquePerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    public static Query<Integer> averageUniquePlayerCount(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectUniquePlayersPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(DISTINCT " + SessionsTable.USER_UUID + ") as player_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";
            String selectAverage = SELECT + "AVG(player_count) as average" + FROM + '(' + selectUniquePlayersPerDay + ") q1";

            return database.query(new QueryStatement<Integer>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public Integer processResults(ResultSet set) throws SQLException {
                    return set.next() ? (int) set.getDouble("average") : 0;
                }
            });
        };
    }

    public static Query<Integer> newPlayerCount(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "COUNT(1) as player_count" +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.REGISTERED + "<=?" +
                AND + UserInfoTable.REGISTERED + ">=?" +
                AND + UserInfoTable.SERVER_UUID + "=?";

        return queryPlayerCount(sql, after, before, serverUUID);
    }

    public static Query<Integer> newPlayerCount(long after, long before) {
        String sql = SELECT + "COUNT(1) as player_count" +
                FROM + UsersTable.TABLE_NAME +
                WHERE + UsersTable.REGISTERED + "<=?" +
                AND + UsersTable.REGISTERED + ">=?";

        return queryPlayerCount(sql, after, before);
    }

    public static Query<Map<ServerUUID, Integer>> newPlayerCounts(long after, long before) {
        String sql = SELECT + UserInfoTable.SERVER_UUID + ",COUNT(1) as player_count" +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.REGISTERED + "<=?" +
                AND + UserInfoTable.REGISTERED + ">=?" +
                GROUP_BY + UserInfoTable.SERVER_UUID;

        return new QueryStatement<Map<ServerUUID, Integer>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, before);
                statement.setLong(2, after);
            }

            @Override
            public Map<ServerUUID, Integer> processResults(ResultSet set) throws SQLException {
                Map<ServerUUID, Integer> byServer = new HashMap<>();
                while (set.next()) {
                    byServer.put(ServerUUID.fromString(set.getString(UserInfoTable.SERVER_UUID)), set.getInt("player_count"));
                }
                return byServer;
            }
        };
    }

    /**
     * Fetch a EpochMs - Count map of new players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @param serverUUID     UUID of the Plan server
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many new players joined that day
     */
    public static Query<NavigableMap<Long, Integer>> newPlayerCounts(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectNewPlayersQuery = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + UserInfoTable.REGISTERED + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as player_count" +
                    FROM + UserInfoTable.TABLE_NAME +
                    WHERE + UserInfoTable.REGISTERED + "<=?" +
                    AND + UserInfoTable.REGISTERED + ">=?" +
                    AND + UserInfoTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectNewPlayersQuery, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> newPerDay = new TreeMap<>();
                    while (set.next()) {
                        newPerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return newPerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of new players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @param serverUUID     UUID of the Plan server
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many new players joined that day
     */
    public static Query<NavigableMap<Long, Integer>> hourlyNewPlayerCounts(long after, long before, long timeZoneOffset, UUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectNewPlayersQuery = SELECT +
                    sql.dateToEpochSecond(sql.dateToHourStamp(sql.epochSecondToDate('(' + UserInfoTable.REGISTERED + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as player_count" +
                    FROM + UserInfoTable.TABLE_NAME +
                    WHERE + UserInfoTable.REGISTERED + "<=?" +
                    AND + UserInfoTable.REGISTERED + ">=?" +
                    AND + UserInfoTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectNewPlayersQuery, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> newPerDay = new TreeMap<>();
                    while (set.next()) {
                        newPerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return newPerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of new players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many new players joined that day
     */
    public static Query<NavigableMap<Long, Integer>> newPlayerCounts(long after, long before, long timeZoneOffset) {
        return database -> {
            Sql sql = database.getSql();
            String selectNewPlayersQuery = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + UserInfoTable.REGISTERED + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as player_count" +
                    FROM + UsersTable.TABLE_NAME +
                    WHERE + UsersTable.REGISTERED + "<=?" +
                    AND + UsersTable.REGISTERED + ">=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectNewPlayersQuery, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> newPerDay = new TreeMap<>();
                    while (set.next()) {
                        newPerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return newPerDay;
                }
            });
        };
    }

    /**
     * Fetch a EpochMs - Count map of new players on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset from {@link java.util.TimeZone#getOffset(long)}, applied to the dates before grouping.
     * @return Map: Epoch ms (Start of day at 0 AM, no offset) - How many new players joined that day
     */
    public static Query<NavigableMap<Long, Integer>> hourlyNewPlayerCounts(long after, long before, long timeZoneOffset) {
        return database -> {
            Sql sql = database.getSql();
            String selectNewPlayersQuery = SELECT +
                    sql.dateToEpochSecond(sql.dateToHourStamp(sql.epochSecondToDate('(' + UserInfoTable.REGISTERED + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as player_count" +
                    FROM + UsersTable.TABLE_NAME +
                    WHERE + UsersTable.REGISTERED + "<=?" +
                    AND + UsersTable.REGISTERED + ">=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectNewPlayersQuery, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                }

                @Override
                public NavigableMap<Long, Integer> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Integer> newPerDay = new TreeMap<>();
                    while (set.next()) {
                        newPerDay.put(set.getLong("date"), set.getInt("player_count"));
                    }
                    return newPerDay;
                }
            });
        };
    }

    public static Query<Integer> averageNewPlayerCount(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectNewPlayersQuery = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + UserInfoTable.REGISTERED + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as player_count" +
                    FROM + UserInfoTable.TABLE_NAME +
                    WHERE + UserInfoTable.REGISTERED + "<=?" +
                    AND + UserInfoTable.REGISTERED + ">=?" +
                    AND + UserInfoTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";
            String selectAverage = SELECT + "AVG(player_count) as average" + FROM + '(' + selectNewPlayersQuery + ") q1";

            return database.query(new QueryStatement<Integer>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public Integer processResults(ResultSet set) throws SQLException {
                    return set.next() ? (int) set.getDouble("average") : 0;
                }
            });
        };
    }

    public static Query<Integer> retainedPlayerCount(long after, long before, ServerUUID serverUUID) {
        String selectNewUUIDs = SELECT + UserInfoTable.USER_UUID +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.REGISTERED + ">=?" +
                AND + UserInfoTable.REGISTERED + "<=?" +
                AND + UserInfoTable.SERVER_UUID + "=?";

        String selectUniqueUUIDs = SELECT + DISTINCT + SessionsTable.USER_UUID +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_START + ">=?" +
                AND + SessionsTable.SESSION_END + "<=?" +
                AND + SessionsTable.SERVER_UUID + "=?";

        String sql = SELECT + "COUNT(1) as player_count" +
                FROM + '(' + selectNewUUIDs + ") q1" +
                INNER_JOIN + '(' + selectUniqueUUIDs + ") q2 on q1." + UserInfoTable.USER_UUID + "=q2." + SessionsTable.USER_UUID;

        return new QueryStatement<Integer>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
                statement.setString(3, serverUUID.toString());

                // Have played in the last half of the time frame
                long half = before - (before - after) / 2;
                statement.setLong(4, half);
                statement.setLong(5, before);
                statement.setString(6, serverUUID.toString());
            }

            @Override
            public Integer processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getInt("player_count") : 0;
            }
        };
    }

    public static Query<Integer> operators(ServerUUID serverUUID) {
        String sql = SELECT + "COUNT(1) as player_count" + FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.SERVER_UUID + "=?" +
                AND + UserInfoTable.OP + "=?";
        return new QueryStatement<Integer>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setBoolean(2, true);
            }

            @Override
            public Integer processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getInt("player_count") : 0;
            }
        };
    }
}