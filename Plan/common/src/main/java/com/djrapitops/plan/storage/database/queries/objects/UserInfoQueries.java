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

import com.djrapitops.plan.gathering.domain.UserInfo;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryAllStatement;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.utilities.java.Lists;
import org.apache.commons.text.TextStringBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Queries for {@link UserInfo} objects.
 *
 * @author AuroraLS3
 */
public class UserInfoQueries {

    private UserInfoQueries() {
        /* Static method class */
    }

    /**
     * Query database for user information.
     * <p>
     * The user information does not contain player names.
     *
     * @return Map: Server UUID - List of user information
     */
    public static Query<Map<ServerUUID, List<UserInfo>>> fetchAllUserInformation() {
        String sql = SELECT +
                UserInfoTable.REGISTERED + ',' +
                UserInfoTable.BANNED + ',' +
                UserInfoTable.OP + ',' +
                UserInfoTable.USER_UUID + ',' +
                UserInfoTable.SERVER_UUID + ',' +
                UserInfoTable.JOIN_ADDRESS +
                FROM + UserInfoTable.TABLE_NAME;

        return new QueryAllStatement<Map<ServerUUID, List<UserInfo>>>(sql, 50000) {
            @Override
            public Map<ServerUUID, List<UserInfo>> processResults(ResultSet set) throws SQLException {
                Map<ServerUUID, List<UserInfo>> serverMap = new HashMap<>();
                while (set.next()) {
                    ServerUUID serverUUID = ServerUUID.fromString(set.getString(UserInfoTable.SERVER_UUID));
                    UUID uuid = UUID.fromString(set.getString(UserInfoTable.USER_UUID));

                    List<UserInfo> userInfos = serverMap.computeIfAbsent(serverUUID, Lists::create);

                    long registered = set.getLong(UserInfoTable.REGISTERED);
                    boolean banned = set.getBoolean(UserInfoTable.BANNED);
                    boolean op = set.getBoolean(UserInfoTable.OP);
                    String joinAddress = set.getString(UserInfoTable.JOIN_ADDRESS);

                    userInfos.add(new UserInfo(uuid, serverUUID, registered, op, joinAddress, banned));
                }
                return serverMap;
            }
        };
    }

    /**
     * Query database for User information of a specific player.
     *
     * @param playerUUID UUID of the player.
     * @return List of UserInfo objects, one for each server where the player has played.
     */
    public static Query<Set<UserInfo>> fetchUserInformationOfUser(UUID playerUUID) {
        String sql = SELECT +
                UserInfoTable.TABLE_NAME + '.' + UserInfoTable.REGISTERED + ',' +
                UserInfoTable.BANNED + ',' +
                UserInfoTable.OP + ',' +
                UserInfoTable.SERVER_UUID + ',' +
                UserInfoTable.JOIN_ADDRESS +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.TABLE_NAME + '.' + UserInfoTable.USER_UUID + "=?";

        return new QueryStatement<Set<UserInfo>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
            }

            @Override
            public Set<UserInfo> processResults(ResultSet set) throws SQLException {
                Set<UserInfo> userInformation = new HashSet<>();
                while (set.next()) {
                    long registered = set.getLong(UserInfoTable.REGISTERED);
                    boolean op = set.getBoolean(UserInfoTable.OP);
                    boolean banned = set.getBoolean(UserInfoTable.BANNED);
                    ServerUUID serverUUID = ServerUUID.fromString(set.getString(UserInfoTable.SERVER_UUID));
                    String joinAddress = set.getString(UserInfoTable.JOIN_ADDRESS);

                    userInformation.add(new UserInfo(playerUUID, serverUUID, registered, op, joinAddress, banned));
                }
                return userInformation;
            }
        };
    }

    /**
     * Query database for all User information of a specific server.
     *
     * @param serverUUID UUID of the Plan server.
     * @return Map: Player UUID - user information
     */
    public static Query<Map<UUID, UserInfo>> fetchUserInformationOfServer(ServerUUID serverUUID) {
        String sql = SELECT +
                UserInfoTable.REGISTERED + ',' +
                UserInfoTable.BANNED + ',' +
                UserInfoTable.JOIN_ADDRESS + ',' +
                UserInfoTable.OP + ',' +
                UserInfoTable.USER_UUID + ',' +
                UserInfoTable.SERVER_UUID +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.SERVER_UUID + "=?";

        return new QueryStatement<Map<UUID, UserInfo>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public Map<UUID, UserInfo> processResults(ResultSet set) throws SQLException {
                Map<UUID, UserInfo> userInformation = new HashMap<>();
                while (set.next()) {
                    ServerUUID serverUUID = ServerUUID.fromString(set.getString(UserInfoTable.SERVER_UUID));
                    UUID uuid = UUID.fromString(set.getString(UserInfoTable.USER_UUID));

                    long registered = set.getLong(UserInfoTable.REGISTERED);
                    boolean banned = set.getBoolean(UserInfoTable.BANNED);
                    boolean op = set.getBoolean(UserInfoTable.OP);

                    String joinAddress = set.getString(UserInfoTable.JOIN_ADDRESS);

                    userInformation.put(uuid, new UserInfo(uuid, serverUUID, registered, op, joinAddress, banned));
                }
                return userInformation;
            }
        };
    }

    public static Query<Map<UUID, Long>> fetchRegisterDates(long after, long before, ServerUUID serverUUID) {
        String sql = SELECT +
                UserInfoTable.USER_UUID + ',' +
                UserInfoTable.REGISTERED +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.SERVER_UUID + "=?" +
                AND + UserInfoTable.REGISTERED + ">=?" +
                AND + UserInfoTable.REGISTERED + "<=?";

        return new QueryStatement<Map<UUID, Long>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public Map<UUID, Long> processResults(ResultSet set) throws SQLException {
                Map<UUID, Long> registerDates = new HashMap<>();
                while (set.next()) {
                    registerDates.put(
                            UUID.fromString(set.getString(UserInfoTable.USER_UUID)),
                            set.getLong(UserInfoTable.REGISTERED)
                    );
                }
                return registerDates;
            }
        };
    }

    public static Query<Map<String, Integer>> joinAddresses() {
        String sql = SELECT +
                "COUNT(1) as total," +
                "LOWER(COALESCE(" + UserInfoTable.JOIN_ADDRESS + ", ?)) as address" +
                FROM + '(' +
                SELECT + DISTINCT +
                UserInfoTable.USER_UUID + ',' +
                UserInfoTable.JOIN_ADDRESS +
                FROM + UserInfoTable.TABLE_NAME +
                ") q1" +
                GROUP_BY + "address" +
                ORDER_BY + "address ASC";

        return new QueryStatement<Map<String, Integer>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, "Unknown");
            }

            @Override
            public Map<String, Integer> processResults(ResultSet set) throws SQLException {
                Map<String, Integer> joinAddresses = new TreeMap<>();
                while (set.next()) {
                    joinAddresses.put(set.getString("address"), set.getInt("total"));
                }
                return joinAddresses;
            }
        };
    }

    public static Query<Map<String, Integer>> joinAddresses(ServerUUID serverUUID) {
        String sql = SELECT +
                "COUNT(1) as total," +
                "LOWER(COALESCE(" + UserInfoTable.JOIN_ADDRESS + ", ?)) as address" +
                FROM + UserInfoTable.TABLE_NAME +
                WHERE + UserInfoTable.SERVER_UUID + "=?" +
                GROUP_BY + "address" +
                ORDER_BY + "address ASC";

        return new QueryStatement<Map<String, Integer>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, "Unknown");
                statement.setString(2, serverUUID.toString());
            }

            @Override
            public Map<String, Integer> processResults(ResultSet set) throws SQLException {
                Map<String, Integer> joinAddresses = new TreeMap<>();
                while (set.next()) {
                    joinAddresses.put(set.getString("address"), set.getInt("total"));
                }
                return joinAddresses;
            }
        };
    }

    public static Query<List<String>> uniqueJoinAddresses() {
        String sql = SELECT + DISTINCT + "LOWER(COALESCE(" + UserInfoTable.JOIN_ADDRESS + ", ?)) as address" +
                FROM + UserInfoTable.TABLE_NAME +
                ORDER_BY + "address ASC";
        return new QueryStatement<List<String>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, "unknown");
            }

            @Override
            public List<String> processResults(ResultSet set) throws SQLException {
                List<String> joinAddresses = new ArrayList<>();
                while (set.next()) joinAddresses.add(set.getString("address"));
                return joinAddresses;
            }
        };
    }

    public static Query<Set<UUID>> uuidsOfOperators() {
        return getUUIDsForBooleanGroup(UserInfoTable.OP, true);
    }

    public static Query<Set<UUID>> getUUIDsForBooleanGroup(String column, boolean value) {
        String sql = SELECT + UserInfoTable.USER_UUID + FROM + UserInfoTable.TABLE_NAME +
                WHERE + column + "=?";
        return new QueryStatement<Set<UUID>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setBoolean(1, value);
            }

            @Override
            public Set<UUID> processResults(ResultSet set) throws SQLException {
                return extractUUIDs(set);
            }
        };
    }

    public static Set<UUID> extractUUIDs(ResultSet set) throws SQLException {
        Set<UUID> uuids = new HashSet<>();
        while (set.next()) {
            uuids.add(UUID.fromString(set.getString(UserInfoTable.USER_UUID)));
        }
        return uuids;
    }

    public static Query<Set<UUID>> uuidsOfNonOperators() {
        return getUUIDsForBooleanGroup(UserInfoTable.OP, false);
    }

    public static Query<Set<UUID>> uuidsOfBanned() {
        return getUUIDsForBooleanGroup(UserInfoTable.BANNED, true);
    }

    public static Query<Set<UUID>> uuidsOfNotBanned() {
        return getUUIDsForBooleanGroup(UserInfoTable.BANNED, false);
    }

    public static Query<Set<UUID>> uuidsOfPlayersWithJoinAddresses(List<String> joinAddresses) {
        String selectLowercaseJoinAddresses = SELECT +
                UserInfoTable.USER_UUID + ',' +
                "LOWER(COALESCE(" + UserInfoTable.JOIN_ADDRESS + ", ?)) as address" +
                FROM + UserInfoTable.TABLE_NAME;
        String sql = SELECT + DISTINCT + UserInfoTable.USER_UUID +
                FROM + '(' + selectLowercaseJoinAddresses + ") q1" +
                WHERE + "address IN (" +
                new TextStringBuilder().appendWithSeparators(joinAddresses.stream().map(item -> '?').iterator(), ",") +
                ')'; // Don't append addresses directly, SQL injection hazard

        return new QueryStatement<Set<UUID>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, "unknown");
                for (int i = 1; i <= joinAddresses.size(); i++) {
                    String address = joinAddresses.get(i - 1);
                    statement.setString(i + 1, address);
                }
            }

            @Override
            public Set<UUID> processResults(ResultSet set) throws SQLException {
                return extractUUIDs(set);
            }
        };
    }
}