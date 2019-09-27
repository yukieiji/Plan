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
package utilities;

import com.djrapitops.plan.gathering.domain.*;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.storage.database.transactions.StoreServerInformationTransaction;
import com.djrapitops.plan.storage.database.transactions.Transaction;
import com.djrapitops.plan.storage.database.transactions.events.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Class for saving test data to a database.
 *
 * @author Rsl1122
 */
public class TestData {

    private TestData() {
        /* Utility class */
    }

    private static UUID playerUUID = TestConstants.PLAYER_ONE_UUID;
    private static UUID player2UUID = TestConstants.PLAYER_TWO_UUID;
    private static UUID serverUUID = TestConstants.SERVER_UUID;
    private static UUID server2UUID = TestConstants.SERVER_TWO_UUID;
    private static String playerName = TestConstants.PLAYER_ONE_NAME;
    private static String player2Name = TestConstants.PLAYER_TWO_NAME;

    private static String[] serverWorldNames = new String[]{
            TestConstants.WORLD_ONE_NAME, "World Two", "world"
    };
    private static String[] server2WorldNames = new String[]{
            "Foo", "Bar", "Z"
    };

    private static long playerFirstJoin = 1234500L;
    private static long playerSecondJoin = 234000L;

    private static List<Session> playerSessions = createSessionsForPlayer(playerUUID);
    private static List<Session> player2Sessions = createSessionsForPlayer(player2UUID);

    private static List<GeoInfo> playerGeoInfo = createGeoInfoForPlayer();

    private static List<GeoInfo> createGeoInfoForPlayer() {
        List<GeoInfo> geoInfos = new ArrayList<>();

        geoInfos.add(new GeoInfo("Not Known", playerFirstJoin));
        geoInfos.add(new GeoInfo("Not Known", playerFirstJoin));
        geoInfos.add(new GeoInfo("Local Machine", playerFirstJoin));
        geoInfos.add(new GeoInfo("Argentina", playerFirstJoin));

        return geoInfos;
    }

    private static List<Session> createSessionsForPlayer(UUID uuid) {
        List<Session> sessions = new ArrayList<>();

        String[] gms = GMTimes.getGMKeyArray();

        Session sessionOne = new Session(uuid, serverUUID, playerFirstJoin, serverWorldNames[0], gms[0]);

        UUID otherUUID = uuid.equals(playerUUID) ? player2UUID : playerUUID;
        sessionOne.playerKilled(new PlayerKill(otherUUID, "Iron Sword", 1234750L));
        sessionOne.playerKilled(new PlayerKill(otherUUID, "Gold Sword", 1234800L));

        sessionOne.endSession(1235000L); // Length 500ms
        sessions.add(sessionOne);

        Session sessionTwo = new Session(uuid, server2UUID, playerSecondJoin, server2WorldNames[0], gms[1]);
        sessionTwo.changeState(server2WorldNames[1], gms[0], 334000L); // Length 100s
        sessionTwo.endSession(434000L); // Length 200s
        sessions.add(sessionTwo);

        return sessions;
    }

    public static Transaction storeServers() {
        return new Transaction() {
            @Override
            protected void performOperations() {
                executeOther(new StoreServerInformationTransaction(new Server(-1, serverUUID, "Server 1", "", 20)));
                executeOther(new StoreServerInformationTransaction(new Server(-1, server2UUID, "Server 2", "", 50)));

                for (String worldName : serverWorldNames) {
                    executeOther(new WorldNameStoreTransaction(serverUUID, worldName));
                }
                for (String worldName : server2WorldNames) {
                    executeOther(new WorldNameStoreTransaction(server2UUID, worldName));
                }
            }
        };
    }

    public static Transaction[] storePlayerOneData() {
        return new Transaction[]{
                new PlayerRegisterTransaction(playerUUID, () -> playerFirstJoin, playerName),
                new Transaction() {
                    @Override
                    protected void performOperations() {
                        executeOther(new PlayerServerRegisterTransaction(playerUUID, () -> playerFirstJoin, playerName, serverUUID));
                        executeOther(new PlayerServerRegisterTransaction(playerUUID, () -> playerSecondJoin, playerName, server2UUID));

                        for (GeoInfo geoInfo : playerGeoInfo) {
                            executeOther(new GeoInfoStoreTransaction(playerUUID, geoInfo));
                        }

                        for (Session session : playerSessions) {
                            executeOther(new SessionEndTransaction(session));
                        }
                    }
                }
        };
    }

    public static Transaction[] storePlayerTwoData() {
        return new Transaction[]{
                new PlayerRegisterTransaction(player2UUID, () -> playerFirstJoin, player2Name),
                new Transaction() {
                    @Override
                    protected void performOperations() {
                        executeOther(new PlayerServerRegisterTransaction(player2UUID, () -> playerFirstJoin, player2Name, serverUUID));
                        executeOther(new PlayerServerRegisterTransaction(player2UUID, () -> playerSecondJoin, player2Name, server2UUID));

                        for (GeoInfo geoInfo : playerGeoInfo) {
                            executeOther(new GeoInfoStoreTransaction(player2UUID, geoInfo));
                        }

                        for (Session session : player2Sessions) {
                            executeOther(new SessionEndTransaction(session));
                        }
                    }
                }
        };
    }

    public static String[] getServerWorldNames() {
        return serverWorldNames;
    }

    public static String[] getServer2WorldNames() {
        return server2WorldNames;
    }

    public static List<Session> getPlayerSessions() {
        return playerSessions;
    }

    public static List<Session> getPlayer2Sessions() {
        return player2Sessions;
    }

    public static List<GeoInfo> getPlayerGeoInfo() {
        return playerGeoInfo;
    }

    public static BaseUser getPlayerBaseUser() {
        return new BaseUser(playerUUID, playerName, playerFirstJoin, 0);
    }

    public static BaseUser getPlayer2BaseUser() {
        return new BaseUser(player2UUID, player2Name, playerFirstJoin, 0);
    }
}