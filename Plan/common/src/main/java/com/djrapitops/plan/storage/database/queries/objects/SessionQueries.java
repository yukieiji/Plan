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
package com.djrapitops.plan.storage.database.queries.objects;

import com.djrapitops.plan.delivery.domain.DateHolder;
import com.djrapitops.plan.delivery.domain.PlayerName;
import com.djrapitops.plan.delivery.domain.ServerName;
import com.djrapitops.plan.delivery.domain.mutators.SessionsMutator;
import com.djrapitops.plan.gathering.domain.*;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryAllStatement;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.building.Sql;
import com.djrapitops.plan.storage.database.sql.tables.*;
import com.djrapitops.plan.utilities.comparators.DateHolderRecentComparator;
import com.djrapitops.plan.utilities.java.Maps;
import org.apache.commons.text.TextStringBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Queries for {@link FinishedSession} objects.
 *
 * @author AuroraLS3
 */
public class SessionQueries {

    private SessionQueries() {
        /* Static method class */
    }

    private static final String SELECT_SESSIONS_STATEMENT = SELECT +
            "s." + SessionsTable.ID + ',' +
            "s." + SessionsTable.USER_UUID + ',' +
            "s." + SessionsTable.SERVER_UUID + ',' +
            "u." + UsersTable.USER_NAME + " as name," +
            "u_info." + UserInfoTable.REGISTERED + " as registered," +
            "server." + ServerTable.NAME + " as server_name," +
            "server." + ServerTable.SERVER_ID + " as server_id," +
            SessionsTable.SESSION_START + ',' +
            SessionsTable.SESSION_END + ',' +
            SessionsTable.MOB_KILLS + ',' +
            SessionsTable.DEATHS + ',' +
            SessionsTable.AFK_TIME + ',' +
            WorldTimesTable.SURVIVAL + ',' +
            WorldTimesTable.CREATIVE + ',' +
            WorldTimesTable.ADVENTURE + ',' +
            WorldTimesTable.SPECTATOR + ',' +
            WorldTable.NAME + ',' +
            KillsTable.KILLER_UUID + ',' +
            KillsTable.VICTIM_UUID + ',' +
            "v." + UsersTable.USER_NAME + " as victim_name, " +
            KillsTable.DATE + ',' +
            KillsTable.WEAPON +
            FROM + SessionsTable.TABLE_NAME + " s" +
            INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.USER_UUID + "=s." + SessionsTable.USER_UUID +
            INNER_JOIN + ServerTable.TABLE_NAME + " server on server." + ServerTable.SERVER_UUID + "=s." + SessionsTable.SERVER_UUID +
            LEFT_JOIN + UserInfoTable.TABLE_NAME + " u_info on (u_info." + UserInfoTable.USER_UUID + "=s." + SessionsTable.USER_UUID + AND + "u_info." + UserInfoTable.SERVER_UUID + "=s." + SessionsTable.SERVER_UUID + ')' +
            LEFT_JOIN + KillsTable.TABLE_NAME + " ON " + "s." + SessionsTable.ID + '=' + KillsTable.TABLE_NAME + '.' + KillsTable.SESSION_ID +
            LEFT_JOIN + UsersTable.TABLE_NAME + " v on v." + UsersTable.USER_UUID + '=' + KillsTable.VICTIM_UUID +
            INNER_JOIN + WorldTimesTable.TABLE_NAME + " ON s." + SessionsTable.ID + '=' + WorldTimesTable.TABLE_NAME + '.' + WorldTimesTable.SESSION_ID +
            INNER_JOIN + WorldTable.TABLE_NAME + " ON " + WorldTimesTable.TABLE_NAME + '.' + WorldTimesTable.WORLD_ID + '=' + WorldTable.TABLE_NAME + '.' + WorldTable.ID;

    private static final String ORDER_BY_SESSION_START_DESC = ORDER_BY + SessionsTable.SESSION_START + " DESC";

    /**
     * Query the database for Session data with kill, death or world data.
     *
     * @return List of sessions
     */
    public static Query<List<FinishedSession>> fetchAllSessions() {
        String sql = SELECT_SESSIONS_STATEMENT +
                ORDER_BY_SESSION_START_DESC;
        return new QueryAllStatement<List<FinishedSession>>(sql, 50000) {
            @Override
            public List<FinishedSession> processResults(ResultSet set) throws SQLException {
                return extractDataFromSessionSelectStatement(set);
            }
        };
    }

    /**
     * Query the database for Session data of a server with kill and world data.
     *
     * @param serverUUID UUID of the Plan server.
     * @return Map: Player UUID - List of sessions on the server.
     */
    public static Query<Map<UUID, List<FinishedSession>>> fetchSessionsOfServer(ServerUUID serverUUID) {
        return db -> SessionsMutator.sortByPlayers(db.query(fetchSessionsOfServerFlat(serverUUID)));
    }

    public static QueryStatement<List<FinishedSession>> fetchSessionsOfServerFlat(ServerUUID serverUUID) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.SERVER_UUID + "=?" +
                ORDER_BY_SESSION_START_DESC;
        return new QueryStatement<List<FinishedSession>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public List<FinishedSession> processResults(ResultSet set) throws SQLException {
                return extractDataFromSessionSelectStatement(set);
            }
        };
    }

    /**
     * Query the database for Session data of a player with kill and world data.
     *
     * @param playerUUID UUID of the Player.
     * @return Map: Server UUID - List of sessions on the server.
     */
    public static Query<Map<ServerUUID, List<FinishedSession>>> fetchSessionsOfPlayer(UUID playerUUID) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.USER_UUID + "=?" +
                ORDER_BY_SESSION_START_DESC;
        return new QueryStatement<Map<ServerUUID, List<FinishedSession>>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
            }

            @Override
            public Map<ServerUUID, List<FinishedSession>> processResults(ResultSet set) throws SQLException {
                List<FinishedSession> sessions = extractDataFromSessionSelectStatement(set);
                return SessionsMutator.sortByServers(sessions);
            }
        };
    }

    private static List<FinishedSession> extractDataFromSessionSelectStatement(ResultSet set) throws SQLException {
        // Server UUID - Player UUID - Session Start - Session
        Map<ServerUUID, Map<UUID, SortedMap<Long, FinishedSession>>> byServer = new HashMap<>();

        // Utilities
        String[] gms = GMTimes.getGMKeyArray();
        Comparator<DateHolder> mostRecentFirst = new DateHolderRecentComparator();
        Comparator<Long> longRecentComparator = (one, two) -> Long.compare(two, one); // Descending order, most recent first.

        while (set.next()) {
            ServerUUID serverUUID = ServerUUID.fromString(set.getString(SessionsTable.SERVER_UUID));
            Map<UUID, SortedMap<Long, FinishedSession>> serverSessions = byServer.computeIfAbsent(serverUUID, Maps::create);

            UUID playerUUID = UUID.fromString(set.getString(SessionsTable.USER_UUID));
            SortedMap<Long, FinishedSession> playerSessions = serverSessions.computeIfAbsent(playerUUID, key -> new TreeMap<>(longRecentComparator));

            long sessionStart = set.getLong(SessionsTable.SESSION_START);
            // id, uuid, serverUUID, sessionStart, sessionEnd, mobKills, deaths, afkTime
            FinishedSession session = playerSessions.getOrDefault(sessionStart, new FinishedSession(
                    playerUUID,
                    serverUUID,
                    sessionStart,
                    set.getLong(SessionsTable.SESSION_END),
                    set.getLong(SessionsTable.AFK_TIME),
                    new DataMap()
            ));

            DataMap extraData = session.getExtraData();
            extraData.put(FinishedSession.Id.class, new FinishedSession.Id(set.getInt(SessionsTable.ID)));
            extraData.put(MobKillCounter.class, new MobKillCounter(set.getInt(SessionsTable.MOB_KILLS)));
            extraData.put(DeathCounter.class, new DeathCounter(set.getInt(SessionsTable.DEATHS)));

            Optional<WorldTimes> existingWorldTimes = extraData.get(WorldTimes.class);
            Optional<PlayerKills> existingPlayerKills = extraData.get(PlayerKills.class);

            WorldTimes worldTimes = existingWorldTimes.orElseGet(WorldTimes::new);
            String worldName = set.getString(WorldTable.NAME);

            if (!worldTimes.contains(worldName)) {
                Map<String, Long> gmMap = new HashMap<>();
                gmMap.put(gms[0], set.getLong(WorldTimesTable.SURVIVAL));
                gmMap.put(gms[1], set.getLong(WorldTimesTable.CREATIVE));
                gmMap.put(gms[2], set.getLong(WorldTimesTable.ADVENTURE));
                gmMap.put(gms[3], set.getLong(WorldTimesTable.SPECTATOR));
                GMTimes gmTimes = new GMTimes(gmMap);
                worldTimes.setGMTimesForWorld(worldName, gmTimes);
            }

            if (!existingWorldTimes.isPresent()) extraData.put(WorldTimes.class, worldTimes);

            PlayerKills playerKills = existingPlayerKills.orElseGet(PlayerKills::new);

            String victimName = set.getString("victim_name");
            if (victimName != null) {
                UUID killer = UUID.fromString(set.getString(KillsTable.KILLER_UUID));
                UUID victim = UUID.fromString(set.getString(KillsTable.VICTIM_UUID));
                long date = set.getLong(KillsTable.DATE);
                String weapon = set.getString(KillsTable.WEAPON);
                PlayerKill newKill = new PlayerKill(killer, victim, weapon, date, victimName);

                if (!playerKills.contains(newKill)) {
                    playerKills.add(newKill);
                }
            }
            if (!existingPlayerKills.isPresent()) extraData.put(PlayerKills.class, playerKills);

            extraData.put(PlayerName.class, new PlayerName(set.getString("name")));
            extraData.put(ServerName.class, new ServerName(
                    Server.getIdentifiableName(
                            set.getString("server_name"),
                            set.getInt("server_id")
                    )));

            session.setAsFirstSessionIfMatches(set.getLong("registered"));

            playerSessions.put(sessionStart, session);
        }

        return byServer.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .map(SortedMap::values)
                .flatMap(Collection::stream)
                .sorted(mostRecentFirst) // Disorder arises
                .collect(Collectors.toList());
    }

    public static Query<List<FinishedSession>> fetchServerSessionsWithoutKillOrWorldData(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT +
                SessionsTable.ID + ',' +
                SessionsTable.USER_UUID + ',' +
                SessionsTable.SESSION_START + ',' +
                SessionsTable.SESSION_END + ',' +
                SessionsTable.DEATHS + ',' +
                SessionsTable.MOB_KILLS + ',' +
                SessionsTable.AFK_TIME +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                AND + SessionsTable.SESSION_START + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";

        return new QueryStatement<List<FinishedSession>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public List<FinishedSession> processResults(ResultSet set) throws SQLException {
                List<FinishedSession> sessions = new ArrayList<>();
                while (set.next()) {
                    UUID uuid = UUID.fromString(set.getString(SessionsTable.USER_UUID));
                    long start = set.getLong(SessionsTable.SESSION_START);
                    long end = set.getLong(SessionsTable.SESSION_END);

                    int deaths = set.getInt(SessionsTable.DEATHS);
                    int mobKills = set.getInt(SessionsTable.MOB_KILLS);
                    int id = set.getInt(SessionsTable.ID);

                    long timeAFK = set.getLong(SessionsTable.AFK_TIME);
                    DataMap extraData = new DataMap();
                    extraData.put(FinishedSession.Id.class, new FinishedSession.Id(id));
                    extraData.put(DeathCounter.class, new DeathCounter(deaths));
                    extraData.put(MobKillCounter.class, new MobKillCounter(mobKills));

                    sessions.add(new FinishedSession(uuid, serverUUID, start, end, timeAFK, extraData));
                }
                return sessions;
            }
        };
    }

    private static Query<Long> fetchLatestSessionStartLimitForServer(ServerUUID serverUUID, int limit) {
        String sql = SELECT + SessionsTable.SESSION_START + FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                ORDER_BY_SESSION_START_DESC + " LIMIT ?";

        return new QueryStatement<Long>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setInt(2, limit);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                Long last = null;
                while (set.next()) {
                    last = set.getLong(SessionsTable.SESSION_START);
                }
                return last;
            }
        };
    }

    private static Query<Long> fetchLatestSessionStartLimit(int limit) {
        String sql = SELECT + SessionsTable.SESSION_START + FROM + SessionsTable.TABLE_NAME +
                ORDER_BY_SESSION_START_DESC + " LIMIT ?";

        return new QueryStatement<Long>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setInt(1, limit);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                Long last = null;
                while (set.next()) {
                    last = set.getLong(SessionsTable.SESSION_START);
                }
                return last;
            }
        };
    }

    public static Query<List<FinishedSession>> fetchLatestSessionsOfServer(ServerUUID serverUUID, int limit) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.SERVER_UUID + "=?" +
                AND + "s." + SessionsTable.SESSION_START + ">=?" +
                ORDER_BY_SESSION_START_DESC;

        return db -> {
            Long start = db.query(fetchLatestSessionStartLimitForServer(serverUUID, limit));
            return db.query(new QueryStatement<List<FinishedSession>>(sql) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setString(1, serverUUID.toString());
                    statement.setLong(2, start != null ? start : 0L);
                }

                @Override
                public List<FinishedSession> processResults(ResultSet set) throws SQLException {
                    return extractDataFromSessionSelectStatement(set);
                }
            });
        };
    }

    public static Query<List<FinishedSession>> fetchLatestSessions(int limit) {
        String sql = SELECT_SESSIONS_STATEMENT
                // Fix for "First Session" icons in the Most recent sessions on network page
                .replace(LEFT_JOIN + UserInfoTable.TABLE_NAME + " u_info on (u_info." + UserInfoTable.USER_UUID + "=s." + SessionsTable.USER_UUID + AND + "u_info." + UserInfoTable.SERVER_UUID + "=s." + SessionsTable.SERVER_UUID + ')', "")
                .replace("u_info", "u") +
                WHERE + "s." + SessionsTable.SESSION_START + ">=?" +
                ORDER_BY_SESSION_START_DESC;
        return db -> {
            Long start = db.query(fetchLatestSessionStartLimit(limit));
            return db.query(new QueryStatement<List<FinishedSession>>(sql) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, start != null ? start : 0L);
                }

                @Override
                public List<FinishedSession> processResults(ResultSet set) throws SQLException {
                    return extractDataFromSessionSelectStatement(set);
                }
            });
        };
    }

    public static Query<Long> sessionCount(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "COUNT(1) as count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("count") : 0L;
            }
        };
    }

    public static Query<Long> sessionCount(long after, long before) {
        String sql = SELECT + "COUNT(1) as count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("count") : 0L;
            }
        };
    }

    /**
     * Query session count for each day within range on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset in ms to determine start of day.
     * @param serverUUID     UUID of the Plan server.
     * @return Map - Epoch ms (Start of day at 0 AM, no offset) : Session count of that day
     */
    public static Query<NavigableMap<Long, Integer>> sessionCountPerDay(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectSessionsPerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "COUNT(1) as session_count" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Integer>>(selectSessionsPerDay, 100) {
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
                        uniquePerDay.put(set.getLong("date"), set.getInt("session_count"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    public static Query<Long> playtime(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("playtime") : 0L;
            }
        };
    }

    public static Query<Map<ServerUUID, Long>> playtimeOfPlayer(long after, long before, UUID playerUUID) {
        String sql = SELECT + SessionsTable.SERVER_UUID + ",SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.USER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?" +
                GROUP_BY + SessionsTable.SERVER_UUID;
        return new QueryStatement<Map<ServerUUID, Long>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Map<ServerUUID, Long> processResults(ResultSet set) throws SQLException {
                Map<ServerUUID, Long> playtimeOfPlayer = new HashMap<>();
                while (set.next()) {
                    playtimeOfPlayer.put(ServerUUID.fromString(set.getString(SessionsTable.SERVER_UUID)), set.getLong("playtime"));
                }
                return playtimeOfPlayer;
            }
        };
    }

    public static Query<Long> playtime(long after, long before) {
        String sql = SELECT + "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("playtime") : 0L;
            }
        };
    }

    /**
     * Query playtime for each day within range on a server.
     *
     * @param after          After epoch ms
     * @param before         Before epoch ms
     * @param timeZoneOffset Offset in ms to determine start of day.
     * @param serverUUID     UUID of the Plan server.
     * @return Map - Epoch ms (Start of day at 0 AM, no offset) : Playtime of that day
     */
    public static Query<NavigableMap<Long, Long>> playtimePerDay(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectPlaytimePerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";

            return database.query(new QueryStatement<NavigableMap<Long, Long>>(selectPlaytimePerDay, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public NavigableMap<Long, Long> processResults(ResultSet set) throws SQLException {
                    NavigableMap<Long, Long> uniquePerDay = new TreeMap<>();
                    while (set.next()) {
                        uniquePerDay.put(set.getLong("date"), set.getLong("playtime"));
                    }
                    return uniquePerDay;
                }
            });
        };
    }

    public static Query<Long> averagePlaytimePerDay(long after, long before, long timeZoneOffset, ServerUUID serverUUID) {
        return database -> {
            Sql sql = database.getSql();
            String selectPlaytimePerDay = SELECT +
                    sql.dateToEpochSecond(sql.dateToDayStamp(sql.epochSecondToDate('(' + SessionsTable.SESSION_START + "+?)/1000"))) +
                    "*1000 as date," +
                    "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + "date";
            String selectAverage = SELECT + "AVG(playtime) as average" + FROM + '(' + selectPlaytimePerDay + ") q1";

            return database.query(new QueryStatement<Long>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, timeZoneOffset);
                    statement.setLong(2, before);
                    statement.setLong(3, after);
                    statement.setString(4, serverUUID.toString());
                }

                @Override
                public Long processResults(ResultSet set) throws SQLException {
                    return set.next() ? (long) set.getDouble("average") : 0;
                }
            });
        };
    }

    public static Query<Long> averagePlaytimePerPlayer(long after, long before, ServerUUID serverUUID) {
        return database -> {
            String selectPlaytimePerPlayer = SELECT +
                    SessionsTable.USER_UUID + "," +
                    "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + SessionsTable.USER_UUID;
            String selectAverage = SELECT + "AVG(playtime) as average" + FROM + '(' + selectPlaytimePerPlayer + ") q1";

            return database.query(new QueryStatement<Long>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, before);
                    statement.setLong(2, after);
                    statement.setString(3, serverUUID.toString());
                }

                @Override
                public Long processResults(ResultSet set) throws SQLException {
                    return set.next() ? (long) set.getDouble("average") : 0;
                }
            });
        };
    }

    /**
     * Fetch average playtime per ALL players.
     *
     * @param after  After epoch ms
     * @param before Before epoch ms
     * @return Average ms played / player, calculated with grouped sums from sessions table.
     */
    public static Query<Long> averagePlaytimePerPlayer(long after, long before) {
        return database -> {
            String selectPlaytimePerPlayer = SELECT +
                    SessionsTable.USER_UUID + "," +
                    "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    GROUP_BY + SessionsTable.USER_UUID;
            String selectAverage = SELECT + "AVG(playtime) as average" + FROM + '(' + selectPlaytimePerPlayer + ") q1";

            return database.query(new QueryStatement<Long>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, before);
                    statement.setLong(2, after);
                }

                @Override
                public Long processResults(ResultSet set) throws SQLException {
                    return set.next() ? (long) set.getDouble("average") : 0;
                }
            });
        };
    }

    public static Query<Long> averageAfkPerPlayer(long after, long before, ServerUUID serverUUID) {
        return database -> {
            String selectAfkPerPlayer = SELECT +
                    SessionsTable.USER_UUID + "," +
                    "SUM(" + SessionsTable.AFK_TIME + ") as afk" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    AND + SessionsTable.SERVER_UUID + "=?" +
                    GROUP_BY + SessionsTable.USER_UUID;
            String selectAverage = SELECT + "AVG(afk) as average" + FROM + '(' + selectAfkPerPlayer + ") q1";

            return database.query(new QueryStatement<Long>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, before);
                    statement.setLong(2, after);
                    statement.setString(3, serverUUID.toString());
                }

                @Override
                public Long processResults(ResultSet set) throws SQLException {
                    return set.next() ? (long) set.getDouble("average") : 0;
                }
            });
        };
    }

    /**
     * Fetch average Afk per ALL players.
     *
     * @param after  After epoch ms
     * @param before Before epoch ms
     * @return Average ms afk / player, calculated with grouped sums from sessions table.
     */
    public static Query<Long> averageAfkPerPlayer(long after, long before) {
        return database -> {
            String selectAfkPerPlayer = SELECT +
                    SessionsTable.USER_UUID + "," +
                    "SUM(" + SessionsTable.AFK_TIME + ") as afk" +
                    FROM + SessionsTable.TABLE_NAME +
                    WHERE + SessionsTable.SESSION_END + "<=?" +
                    AND + SessionsTable.SESSION_START + ">=?" +
                    GROUP_BY + SessionsTable.USER_UUID;
            String selectAverage = SELECT + "AVG(afk) as average" + FROM + '(' + selectAfkPerPlayer + ") q1";

            return database.query(new QueryStatement<Long>(selectAverage, 100) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, before);
                    statement.setLong(2, after);
                }

                @Override
                public Long processResults(ResultSet set) throws SQLException {
                    return set.next() ? (long) set.getDouble("average") : 0;
                }
            });
        };
    }

    public static Query<Long> afkTime(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "SUM(" + SessionsTable.AFK_TIME + ") as afk_time" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("afk_time") : 0L;
            }
        };
    }

    public static Query<Long> afkTime(long after, long before) {
        String sql = SELECT + "SUM(" + SessionsTable.AFK_TIME + ") as afk_time" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("afk_time") : 0L;
            }
        };
    }

    public static Query<Map<String, Long>> playtimePerServer(long after, long before) {
        String sql = SELECT +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime," +
                "s." + ServerTable.SERVER_ID + ',' +
                "s." + ServerTable.NAME +
                FROM + SessionsTable.TABLE_NAME +
                INNER_JOIN + ServerTable.TABLE_NAME + " s on s." + ServerTable.SERVER_UUID + '=' + SessionsTable.TABLE_NAME + '.' + SessionsTable.SERVER_UUID +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?" +
                GROUP_BY + "s." + ServerTable.SERVER_ID;
        return new QueryStatement<Map<String, Long>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Map<String, Long> processResults(ResultSet set) throws SQLException {
                Map<String, Long> playtimePerServer = new HashMap<>();
                while (set.next()) {
                    String name = Server.getIdentifiableName(
                            set.getString(ServerTable.NAME),
                            set.getInt(ServerTable.SERVER_ID)
                    );
                    playtimePerServer.put(name, set.getLong("playtime"));
                }
                return playtimePerServer;
            }
        };
    }

    public static Query<Long> lastSeen(UUID playerUUID, ServerUUID serverUUID) {
        String sql = SELECT + "MAX(" + SessionsTable.SESSION_END + ") as last_seen" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.USER_UUID + "=?" +
                AND + SessionsTable.SERVER_UUID + "=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverUUID.toString());
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("last_seen") : 0;
            }
        };
    }

    public static Query<Long> activePlaytime(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT + "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME +
                ") as playtime" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SERVER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("playtime") : 0L;
            }
        };
    }

    public static Query<Set<UUID>> uuidsOfPlayedBetween(long after, long before) {
        String sql = SELECT + DISTINCT + SessionsTable.USER_UUID +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?";
        return new QueryStatement<Set<UUID>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Set<UUID> processResults(ResultSet set) throws SQLException {
                Set<UUID> uuids = new HashSet<>();
                while (set.next()) {
                    uuids.add(UUID.fromString(set.getString(SessionsTable.USER_UUID)));
                }
                return uuids;
            }
        };
    }

    public static Query<Map<String, Long>> summaryOfPlayers(Set<UUID> playerUUIDs, long after, long before) {
        String selectAggregates = SELECT +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime," +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME + ") as active_playtime," +
                "COUNT(1) as session_count" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.SESSION_START + ">?" +
                AND + SessionsTable.SESSION_END + "<?" +
                AND + SessionsTable.USER_UUID + " IN ('" +
                new TextStringBuilder().appendWithSeparators(playerUUIDs, "','").build() + "')";

        return new QueryStatement<Map<String, Long>>(selectAggregates) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, after);
                statement.setLong(2, before);
            }

            @Override
            public Map<String, Long> processResults(ResultSet set) throws SQLException {
                if (set.next()) {
                    long sessionCount = set.getLong("session_count");
                    long playtime = set.getLong("playtime");
                    long activePlaytime = set.getLong("active_playtime");
                    int playerCount = playerUUIDs.size();
                    return Maps.builder(String.class, Long.class)
                            .put("total_playtime", playtime)
                            .put("average_playtime", playerCount != 0 ? playtime / playerCount : -1L)
                            .put("total_afk_playtime", playtime - activePlaytime)
                            .put("average_afk_playtime", playerCount != 0 ? (playtime - activePlaytime) / playerCount : -1L)
                            .put("total_active_playtime", activePlaytime)
                            .put("average_active_playtime", playerCount != 0 ? activePlaytime / playerCount : -1L)
                            .put("total_sessions", sessionCount)
                            .put("average_sessions", playerCount != 0 ? sessionCount / playerCount : -1L)
                            .put("average_session_length", sessionCount != 0 ? playtime / sessionCount : -1L)
                            .build();
                } else {
                    return Collections.emptyMap();
                }
            }
        };
    }

    public static Query<Long> earliestSessionStart() {
        String sql = SELECT + "MIN(" + SessionsTable.SESSION_START + ") as m" +
                FROM + SessionsTable.TABLE_NAME;
        return new QueryAllStatement<Long>(sql) {
            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("m") : -1L;
            }
        };
    }
}