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
import com.djrapitops.plan.delivery.domain.keys.SessionKeys;
import com.djrapitops.plan.delivery.domain.mutators.SessionsMutator;
import com.djrapitops.plan.gathering.domain.GMTimes;
import com.djrapitops.plan.gathering.domain.PlayerKill;
import com.djrapitops.plan.gathering.domain.Session;
import com.djrapitops.plan.gathering.domain.WorldTimes;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryAllStatement;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.parsing.Sql;
import com.djrapitops.plan.storage.database.sql.tables.*;
import com.djrapitops.plan.utilities.comparators.DateHolderRecentComparator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.djrapitops.plan.storage.database.sql.parsing.Sql.*;

/**
 * Queries for {@link Session} objects.
 *
 * @author Rsl1122
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
     * Query the database for Session data without kill, death or world data.
     *
     * @return Multimap: Server UUID - (Player UUID - List of sessions)
     */
    public static Query<Map<UUID, Map<UUID, List<Session>>>> fetchAllSessionsWithoutKillOrWorldData() {
        String sql = SELECT +
                SessionsTable.ID + ',' +
                SessionsTable.USER_UUID + ',' +
                SessionsTable.SERVER_UUID + ',' +
                SessionsTable.SESSION_START + ',' +
                SessionsTable.SESSION_END + ',' +
                SessionsTable.DEATHS + ',' +
                SessionsTable.MOB_KILLS + ',' +
                SessionsTable.AFK_TIME +
                FROM + SessionsTable.TABLE_NAME;

        return new QueryAllStatement<Map<UUID, Map<UUID, List<Session>>>>(sql, 20000) {
            @Override
            public Map<UUID, Map<UUID, List<Session>>> processResults(ResultSet set) throws SQLException {
                Map<UUID, Map<UUID, List<Session>>> map = new HashMap<>();
                while (set.next()) {
                    UUID serverUUID = UUID.fromString(set.getString(SessionsTable.SERVER_UUID));
                    UUID uuid = UUID.fromString(set.getString(SessionsTable.USER_UUID));

                    Map<UUID, List<Session>> sessionsByUser = map.getOrDefault(serverUUID, new HashMap<>());
                    List<Session> sessions = sessionsByUser.getOrDefault(uuid, new ArrayList<>());

                    long start = set.getLong(SessionsTable.SESSION_START);
                    long end = set.getLong(SessionsTable.SESSION_END);

                    int deaths = set.getInt(SessionsTable.DEATHS);
                    int mobKills = set.getInt(SessionsTable.MOB_KILLS);
                    int id = set.getInt(SessionsTable.ID);

                    long timeAFK = set.getLong(SessionsTable.AFK_TIME);

                    sessions.add(new Session(id, uuid, serverUUID, start, end, mobKills, deaths, timeAFK));

                    sessionsByUser.put(uuid, sessions);
                    map.put(serverUUID, sessionsByUser);
                }
                return map;
            }
        };
    }

    /**
     * Query the database for Session data with kill, death or world data.
     *
     * @return List of sessions
     */
    public static Query<List<Session>> fetchAllSessions() {
        String sql = SELECT_SESSIONS_STATEMENT +
                ORDER_BY_SESSION_START_DESC;
        return new QueryAllStatement<List<Session>>(sql, 50000) {
            @Override
            public List<Session> processResults(ResultSet set) throws SQLException {
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
    public static Query<Map<UUID, List<Session>>> fetchSessionsOfServer(UUID serverUUID) {
        return db -> SessionsMutator.sortByPlayers(db.query(fetchSessionsOfServerFlat(serverUUID)));
    }

    public static QueryStatement<List<Session>> fetchSessionsOfServerFlat(UUID serverUUID) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.SERVER_UUID + "=?" +
                ORDER_BY_SESSION_START_DESC;
        return new QueryStatement<List<Session>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public List<Session> processResults(ResultSet set) throws SQLException {
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
    public static Query<Map<UUID, List<Session>>> fetchSessionsOfPlayer(UUID playerUUID) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.USER_UUID + "=?" +
                ORDER_BY_SESSION_START_DESC;
        return new QueryStatement<Map<UUID, List<Session>>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
            }

            @Override
            public Map<UUID, List<Session>> processResults(ResultSet set) throws SQLException {
                List<Session> sessions = extractDataFromSessionSelectStatement(set);
                return SessionsMutator.sortByServers(sessions);
            }
        };
    }

    private static List<Session> extractDataFromSessionSelectStatement(ResultSet set) throws SQLException {
        // Server UUID - Player UUID - Session Start - Session
        Map<UUID, Map<UUID, SortedMap<Long, Session>>> tempSessionMap = new HashMap<>();

        // Utilities
        String[] gms = GMTimes.getGMKeyArray();
        Comparator<DateHolder> dateColderRecentComparator = new DateHolderRecentComparator();
        Comparator<Long> longRecentComparator = (one, two) -> Long.compare(two, one); // Descending order, most recent first.

        while (set.next()) {
            UUID serverUUID = UUID.fromString(set.getString(SessionsTable.SERVER_UUID));
            Map<UUID, SortedMap<Long, Session>> serverSessions = tempSessionMap.getOrDefault(serverUUID, new HashMap<>());

            UUID playerUUID = UUID.fromString(set.getString(SessionsTable.USER_UUID));
            SortedMap<Long, Session> playerSessions = serverSessions.getOrDefault(playerUUID, new TreeMap<>(longRecentComparator));

            long sessionStart = set.getLong(SessionsTable.SESSION_START);
            // id, uuid, serverUUID, sessionStart, sessionEnd, mobKills, deaths, afkTime
            Session session = playerSessions.getOrDefault(sessionStart, new Session(
                    set.getInt(SessionsTable.ID),
                    playerUUID,
                    serverUUID,
                    sessionStart,
                    set.getLong(SessionsTable.SESSION_END),
                    set.getInt(SessionsTable.MOB_KILLS),
                    set.getInt(SessionsTable.DEATHS),
                    set.getLong(SessionsTable.AFK_TIME)
            ));

            WorldTimes worldTimes = session.getValue(SessionKeys.WORLD_TIMES).orElse(new WorldTimes());
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

            String victimName = set.getString("victim_name");
            if (victimName != null) {
                UUID victim = UUID.fromString(set.getString(KillsTable.VICTIM_UUID));
                long date = set.getLong(KillsTable.DATE);
                String weapon = set.getString(KillsTable.WEAPON);
                List<PlayerKill> playerKills = session.getPlayerKills();
                playerKills.add(new PlayerKill(victim, weapon, date, victimName));
                playerKills.sort(dateColderRecentComparator);
            }

            session.putRawData(SessionKeys.NAME, set.getString("name"));
            session.putRawData(SessionKeys.SERVER_NAME, set.getString("server_name"));

            session.setAsFirstSessionIfMatches(set.getLong("registered"));

            playerSessions.put(sessionStart, session);
            serverSessions.put(playerUUID, playerSessions);
            tempSessionMap.put(serverUUID, serverSessions);
        }

        return tempSessionMap.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .map(SortedMap::values)
                .flatMap(Collection::stream)
                .sorted(dateColderRecentComparator) // Disorder arises
                .collect(Collectors.toList());
    }

    public static Query<List<Session>> fetchServerSessionsWithoutKillOrWorldData(long after, long before, UUID serverUUID) {
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

        return new QueryStatement<List<Session>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public List<Session> processResults(ResultSet set) throws SQLException {
                List<Session> sessions = new ArrayList<>();
                while (set.next()) {
                    UUID uuid = UUID.fromString(set.getString(SessionsTable.USER_UUID));
                    long start = set.getLong(SessionsTable.SESSION_START);
                    long end = set.getLong(SessionsTable.SESSION_END);

                    int deaths = set.getInt(SessionsTable.DEATHS);
                    int mobKills = set.getInt(SessionsTable.MOB_KILLS);
                    int id = set.getInt(SessionsTable.ID);

                    long timeAFK = set.getLong(SessionsTable.AFK_TIME);

                    sessions.add(new Session(id, uuid, serverUUID, start, end, mobKills, deaths, timeAFK));
                }
                return sessions;
            }
        };
    }

    private static Query<Long> fetchLatestSessionStartLimitForServer(UUID serverUUID, int limit) {
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

    public static Query<List<Session>> fetchLatestSessionsOfServer(UUID serverUUID, int limit) {
        String sql = SELECT_SESSIONS_STATEMENT +
                WHERE + "s." + SessionsTable.SERVER_UUID + "=?" +
                AND + "s." + SessionsTable.SESSION_START + ">=?" +
                ORDER_BY_SESSION_START_DESC;

        return db -> {
            Long start = db.query(fetchLatestSessionStartLimitForServer(serverUUID, limit));
            return db.query(new QueryStatement<List<Session>>(sql) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setString(1, serverUUID.toString());
                    statement.setLong(2, start != null ? start : 0L);
                }

                @Override
                public List<Session> processResults(ResultSet set) throws SQLException {
                    return extractDataFromSessionSelectStatement(set);
                }
            });
        };
    }

    public static Query<List<Session>> fetchLatestSessions(int limit) {
        String sql = SELECT_SESSIONS_STATEMENT
                // Fix for "First Session" icons in the Most recent sessions on network page
                .replace(LEFT_JOIN + UserInfoTable.TABLE_NAME + " u_info on (u_info." + UserInfoTable.USER_UUID + "=s." + SessionsTable.USER_UUID + AND + "u_info." + UserInfoTable.SERVER_UUID + "=s." + SessionsTable.SERVER_UUID + ')', "")
                .replace("u_info", "u") +
                WHERE + "s." + SessionsTable.SESSION_START + ">=?" +
                ORDER_BY_SESSION_START_DESC;
        return db -> {
            Long start = db.query(fetchLatestSessionStartLimit(limit));
            return db.query(new QueryStatement<List<Session>>(sql) {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setLong(1, start != null ? start : 0L);
                }

                @Override
                public List<Session> processResults(ResultSet set) throws SQLException {
                    return extractDataFromSessionSelectStatement(set);
                }
            });
        };
    }

    public static Query<Long> sessionCount(long after, long before, UUID serverUUID) {
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
    public static Query<NavigableMap<Long, Integer>> sessionCountPerDay(long after, long before, long timeZoneOffset, UUID serverUUID) {
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

    public static Query<Long> playtime(long after, long before, UUID serverUUID) {
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

    public static Query<Map<UUID, Long>> playtimeOfPlayer(long after, long before, UUID playerUUID) {
        String sql = SELECT + SessionsTable.SERVER_UUID + ",SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                FROM + SessionsTable.TABLE_NAME +
                WHERE + SessionsTable.USER_UUID + "=?" +
                AND + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?" +
                GROUP_BY + SessionsTable.SERVER_UUID;
        return new QueryStatement<Map<UUID, Long>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Map<UUID, Long> processResults(ResultSet set) throws SQLException {
                Map<UUID, Long> playtimeOfPlayer = new HashMap<>();
                while (set.next()) {
                    playtimeOfPlayer.put(UUID.fromString(set.getString(SessionsTable.SERVER_UUID)), set.getLong("playtime"));
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
    public static Query<NavigableMap<Long, Long>> playtimePerDay(long after, long before, long timeZoneOffset, UUID serverUUID) {
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

    public static Query<Long> averagePlaytimePerDay(long after, long before, long timeZoneOffset, UUID serverUUID) {
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
                    return set.next() ? set.getLong("average") : 0;
                }
            });
        };
    }

    public static Query<Long> averagePlaytimePerPlayer(long after, long before, UUID serverUUID) {
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
                    return set.next() ? set.getLong("average") : 0;
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
                    return set.next() ? set.getLong("average") : 0;
                }
            });
        };
    }

    public static Query<Long> averageAfkPerPlayer(long after, long before, UUID serverUUID) {
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
                    return set.next() ? set.getLong("average") : 0;
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
                    return set.next() ? set.getLong("average") : 0;
                }
            });
        };
    }

    public static Query<Long> afkTime(long after, long before, UUID serverUUID) {
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
                ServerTable.NAME +
                FROM + SessionsTable.TABLE_NAME +
                INNER_JOIN + ServerTable.TABLE_NAME + " s on s." + ServerTable.SERVER_UUID + '=' + SessionsTable.TABLE_NAME + '.' + SessionsTable.SERVER_UUID +
                WHERE + SessionsTable.SESSION_END + ">=?" +
                AND + SessionsTable.SESSION_START + "<=?" +
                GROUP_BY + ServerTable.NAME;
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
                    playtimePerServer.put(set.getString(ServerTable.NAME), set.getLong("playtime"));
                }
                return playtimePerServer;
            }
        };
    }

    public static Query<Long> lastSeen(UUID playerUUID, UUID serverUUID) {
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
}