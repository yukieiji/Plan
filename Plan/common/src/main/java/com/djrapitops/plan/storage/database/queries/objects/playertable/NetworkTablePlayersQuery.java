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
package com.djrapitops.plan.storage.database.queries.objects.playertable;

import com.djrapitops.plan.delivery.domain.TablePlayer;
import com.djrapitops.plan.delivery.domain.mutators.ActivityIndex;
import com.djrapitops.plan.storage.database.SQLDB;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.queries.analysis.NetworkActivityIndexQueries;
import com.djrapitops.plan.storage.database.sql.tables.GeoInfoTable;
import com.djrapitops.plan.storage.database.sql.tables.SessionsTable;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Query for displaying players on /players page.
 *
 * @author AuroraLS3
 */
public class NetworkTablePlayersQuery implements Query<List<TablePlayer>> {

    private final long date;
    private final long activeMsThreshold;
    private final int xMostRecentPlayers;

    public NetworkTablePlayersQuery(long date, long activeMsThreshold, int xMostRecentPlayers) {
        this.date = date;
        this.activeMsThreshold = activeMsThreshold;
        this.xMostRecentPlayers = xMostRecentPlayers;
    }

    @Override
    public List<TablePlayer> executeQuery(SQLDB db) {
        String selectGeolocations = SELECT + DISTINCT +
                GeoInfoTable.USER_UUID + ", " +
                GeoInfoTable.GEOLOCATION + ", " +
                GeoInfoTable.LAST_USED +
                FROM + GeoInfoTable.TABLE_NAME;
        String selectLatestGeolocationDate = SELECT +
                GeoInfoTable.USER_UUID + ", " +
                "MAX(" + GeoInfoTable.LAST_USED + ") as last_used_g" +
                FROM + GeoInfoTable.TABLE_NAME +
                GROUP_BY + GeoInfoTable.USER_UUID;
        String selectLatestGeolocations = SELECT +
                "g1." + GeoInfoTable.GEOLOCATION + ',' +
                "g1." + GeoInfoTable.USER_UUID +
                FROM + "(" + selectGeolocations + ") AS g1" +
                INNER_JOIN + "(" + selectLatestGeolocationDate + ") AS g2 ON g1.uuid = g2.uuid" +
                WHERE + GeoInfoTable.LAST_USED + "=last_used_g";

        String selectSessionData = SELECT + "s." + SessionsTable.USER_UUID + ',' +
                "MAX(" + SessionsTable.SESSION_END + ") as last_seen," +
                "COUNT(1) as count," +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME + ") as active_playtime" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                GROUP_BY + "s." + SessionsTable.USER_UUID;

        String selectBanned = SELECT + DISTINCT + "ub." + UserInfoTable.USER_UUID +
                FROM + UserInfoTable.TABLE_NAME + " ub" +
                WHERE + UserInfoTable.BANNED + "=?";

        String selectBaseUsers = SELECT +
                "u." + UsersTable.USER_UUID + ',' +
                "u." + UsersTable.USER_NAME + ',' +
                "u." + UsersTable.REGISTERED + ',' +
                "ban." + UserInfoTable.USER_UUID + " as banned," +
                "geo." + GeoInfoTable.GEOLOCATION + ',' +
                "ses.last_seen," +
                "ses.count," +
                "ses.active_playtime," +
                "act.activity_index" +
                FROM + UsersTable.TABLE_NAME + " u" +
                LEFT_JOIN + '(' + selectBanned + ") ban on ban." + UserInfoTable.USER_UUID + "=u." + UsersTable.USER_UUID +
                LEFT_JOIN + '(' + selectLatestGeolocations + ") geo on geo." + GeoInfoTable.USER_UUID + "=u." + UsersTable.USER_UUID +
                LEFT_JOIN + '(' + selectSessionData + ") ses on ses." + SessionsTable.USER_UUID + "=u." + UsersTable.USER_UUID +
                LEFT_JOIN + '(' + NetworkActivityIndexQueries.selectActivityIndexSQL() + ") act on u." + UsersTable.USER_UUID + "=act." + UserInfoTable.USER_UUID +
                ORDER_BY + "ses.last_seen DESC LIMIT ?";

        return db.query(new QueryStatement<List<TablePlayer>>(selectBaseUsers, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setBoolean(1, true);
                NetworkActivityIndexQueries.setSelectActivityIndexSQLParameters(statement, 2, activeMsThreshold, date);
                statement.setInt(10, xMostRecentPlayers);
            }

            @Override
            public List<TablePlayer> processResults(ResultSet set) throws SQLException {
                List<TablePlayer> players = new ArrayList<>();
                while (set.next()) {
                    TablePlayer.Builder player = TablePlayer.builder()
                            .uuid(UUID.fromString(set.getString(UsersTable.USER_UUID)))
                            .name(set.getString(UsersTable.USER_NAME))
                            .geolocation(set.getString(GeoInfoTable.GEOLOCATION))
                            .registered(set.getLong(UsersTable.REGISTERED))
                            .lastSeen(set.getLong("last_seen"))
                            .sessionCount(set.getInt("count"))
                            .activePlaytime(set.getLong("active_playtime"))
                            .activityIndex(new ActivityIndex(set.getDouble("activity_index"), date));
                    if (set.getString("banned") != null) {
                        player.banned();
                    }
                    players.add(player.build());
                }
                return players;
            }
        });
    }
}