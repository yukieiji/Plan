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
package com.djrapitops.plan.storage.database;

import com.djrapitops.plan.delivery.domain.Nickname;
import com.djrapitops.plan.delivery.domain.TablePlayer;
import com.djrapitops.plan.delivery.domain.container.PlayerContainer;
import com.djrapitops.plan.delivery.domain.keys.Key;
import com.djrapitops.plan.delivery.domain.keys.PlayerKeys;
import com.djrapitops.plan.gathering.domain.ActiveSession;
import com.djrapitops.plan.gathering.domain.BaseUser;
import com.djrapitops.plan.gathering.domain.FinishedSession;
import com.djrapitops.plan.gathering.domain.GeoInfo;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.query.QuerySvc;
import com.djrapitops.plan.settings.config.Config;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.storage.database.queries.*;
import com.djrapitops.plan.storage.database.queries.containers.ContainerFetchQueries;
import com.djrapitops.plan.storage.database.queries.containers.ServerPlayerContainersQuery;
import com.djrapitops.plan.storage.database.queries.objects.*;
import com.djrapitops.plan.storage.database.queries.objects.playertable.NetworkTablePlayersQuery;
import com.djrapitops.plan.storage.database.queries.objects.playertable.ServerTablePlayersQuery;
import com.djrapitops.plan.storage.database.sql.building.Sql;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.storage.database.transactions.StoreConfigTransaction;
import com.djrapitops.plan.storage.database.transactions.Transaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemovePlayerTransaction;
import com.djrapitops.plan.storage.database.transactions.events.*;
import com.djrapitops.plan.storage.database.transactions.init.CreateIndexTransaction;
import com.djrapitops.plan.storage.database.transactions.patches.RegisterDateMinimizationPatch;
import com.djrapitops.plan.storage.upkeep.DBCleanTask;
import org.junit.jupiter.api.Test;
import utilities.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.djrapitops.plan.storage.database.sql.building.Sql.SELECT;
import static com.djrapitops.plan.storage.database.sql.building.Sql.WHERE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contains common Database Tests.
 *
 * @author AuroraLS3
 */
public interface DatabaseTest extends DatabaseTestPreparer {

    default void saveUserOne() {
        db().executeTransaction(new PlayerServerRegisterTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new KickStoreTransaction(playerUUID));
    }

    default void saveUserTwo() {
        db().executeTransaction(new PlayerRegisterTransaction(player2UUID, RandomData::randomTime, TestConstants.PLAYER_TWO_NAME));
    }

    default void saveWorld(String worldName) {
        db().executeTransaction(new WorldNameStoreTransaction(serverUUID(), worldName));
    }

    default void saveWorlds(String... worldNames) {
        for (String worldName : worldNames) {
            saveWorld(worldName);
        }
    }

    default void saveTwoWorlds() {
        saveWorlds(worlds);
    }

    @Test
    default void testRemovalSingleUser() {
        saveUserTwo();

        db().executeTransaction(new PlayerServerRegisterTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        saveTwoWorlds();

        FinishedSession session = RandomData.randomSession(serverUUID(), worlds, playerUUID, player2UUID);

        execute(DataStoreQueries.storeSession(session));
        db().executeTransaction(new NicknameStoreTransaction(playerUUID, new Nickname("TestNick", RandomData.randomTime(), serverUUID()), (uuid, name) -> false /* Not cached */));
        db().executeTransaction(new GeoInfoStoreTransaction(playerUUID, new GeoInfo("TestLoc", RandomData.randomTime())));

        assertTrue(db().query(PlayerFetchQueries.isPlayerRegistered(playerUUID)));

        db().executeTransaction(new RemovePlayerTransaction(playerUUID));

        assertFalse(db().query(PlayerFetchQueries.isPlayerRegistered(playerUUID)));
        assertFalse(db().query(PlayerFetchQueries.isPlayerRegisteredOnServer(playerUUID, serverUUID())));
        assertTrue(db().query(NicknameQueries.fetchNicknameDataOfPlayer(playerUUID)).isEmpty());
        assertTrue(db().query(GeoInfoQueries.fetchPlayerGeoInformation(playerUUID)).isEmpty());
        assertQueryIsEmpty(db(), SessionQueries.fetchSessionsOfPlayer(playerUUID));
    }

    default <T extends Map<?, ?>> void assertQueryIsEmpty(Database database, Query<T> query) {
        assertTrue(database.query(query).isEmpty());
    }

    default void saveGeoInfo(UUID uuid, GeoInfo geoInfo) {
        db().executeTransaction(new GeoInfoStoreTransaction(uuid, geoInfo));
    }

    @Test
    default void cleanDoesNotCleanActivePlayers() {
        saveUserOne();
        saveTwoWorlds();

        long sessionStart = System.currentTimeMillis();
        ActiveSession session = new ActiveSession(playerUUID, serverUUID(), sessionStart, worlds[0], "SURVIVAL");
        execute(DataStoreQueries.storeSession(session.toFinishedSession(sessionStart + 22345L)));

        TestPluginLogger logger = new TestPluginLogger();
        new DBCleanTask(
                config(),
                new Locale(),
                dbSystem(),
                new QuerySvc(config(), dbSystem(), serverInfo(), null),
                serverInfo(),
                logger,
                null
        ).cleanOldPlayers(db());

        Collection<BaseUser> found = db().query(BaseUserQueries.fetchServerBaseUsers(serverUUID()));
        assertFalse(found.isEmpty(), "All users were deleted!! D:");
    }

    @Test
    default void playerContainerSupportsAllPlayerKeys() throws IllegalAccessException {
        saveUserOne();
        saveUserTwo();
        saveTwoWorlds();
        FinishedSession session = RandomData.randomSession(serverUUID(), worlds, playerUUID, player2UUID);
        execute(DataStoreQueries.storeSession(session));
        db().executeTransaction(new NicknameStoreTransaction(playerUUID, RandomData.randomNickname(serverUUID()), (uuid, name) -> false /* Not cached */));
        saveGeoInfo(playerUUID, new GeoInfo("TestLoc", RandomData.randomTime()));
        assertTrue(db().query(PlayerFetchQueries.isPlayerRegistered(playerUUID)));
        db().executeTransaction(new PingStoreTransaction(playerUUID, serverUUID(), RandomData.randomIntDateObjects()));

        PlayerContainer playerContainer = db().query(ContainerFetchQueries.fetchPlayerContainer(playerUUID));
        // Active sessions are added after fetching
        playerContainer.putRawData(PlayerKeys.ACTIVE_SESSION, RandomData.randomUnfinishedSession(serverUUID(), worlds, playerUUID));

        List<String> unsupported = new ArrayList<>();
        List<Key> keys = FieldFetcher.getPublicStaticFields(PlayerKeys.class, Key.class);
        for (Key<?> key : keys) {
            if (!playerContainer.supports(key)) {
                unsupported.add(key.getKeyName());
            }
        }

        assertTrue(unsupported.isEmpty(), () -> "Some keys are not supported by PlayerContainer: PlayerKeys." + unsupported.toString());
    }

    @Test
    default void configIsStoredInTheDatabase() {
        PlanConfig config = config();

        db().executeTransaction(new StoreConfigTransaction(serverUUID(), config, System.currentTimeMillis()));

        Optional<Config> foundConfig = db().query(new NewerConfigQuery(serverUUID(), 0));
        assertTrue(foundConfig.isPresent());
        assertEquals(config, foundConfig.get());
    }

    @Test
    default void unchangedConfigDoesNotUpdateInDatabase() {
        configIsStoredInTheDatabase();
        long savedMs = System.currentTimeMillis();

        PlanConfig config = config();

        db().executeTransaction(new StoreConfigTransaction(serverUUID(), config, System.currentTimeMillis()));

        assertFalse(db().query(new NewerConfigQuery(serverUUID(), savedMs)).isPresent());
    }

    @Test
    default void indexCreationWorksWithoutErrors() throws Exception {
        Transaction transaction = new CreateIndexTransaction();
        db().executeTransaction(transaction).get(); // get to ensure transaction is finished
        assertTrue(transaction.wasSuccessful());
    }

    @Test
    default void playerCountForServersIsCorrect() {
        Map<ServerUUID, Integer> expected = Collections.singletonMap(serverUUID(), 1);
        saveUserOne();

        Map<ServerUUID, Integer> result = db().query(ServerAggregateQueries.serverUserCounts());
        assertEquals(expected, result);
    }

    @Test
    default void serverPlayerContainersQueryDoesNotReturnDuplicatePlayers() {
        db().executeTransaction(TestData.storeServers());
        executeTransactions(TestData.storePlayerOneData());
        executeTransactions(TestData.storePlayerTwoData());

        List<UUID> expected = Arrays.asList(playerUUID, player2UUID);
        Collections.sort(expected);

        Collection<UUID> result = db().query(new ServerPlayerContainersQuery(TestConstants.SERVER_UUID))
                .stream().map(player -> player.getUnsafe(PlayerKeys.UUID))
                .sorted()
                .collect(Collectors.toList());

        assertEquals(expected, result);
    }

    @Test
    default void sqlDateConversionSanityCheck() {
        Database db = db();

        long expected = System.currentTimeMillis() / 1000;

        Sql sql = db.getType().getSql();
        String testSQL = SELECT + sql.dateToEpochSecond(sql.epochSecondToDate(Long.toString(expected))) + " as ms";

        long result = db.query(new QueryAllStatement<Long>(testSQL) {
            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("ms") : -1L;
            }
        });
        assertEquals(expected, result);
    }

    @Test
    default void sqlDateParsingSanityCheck() {
        Database db = db();

        long time = System.currentTimeMillis();
        int offset = TimeZone.getDefault().getOffset(time);

        Sql sql = db.getType().getSql();
        String testSQL = SELECT + sql.dateToDayStamp(sql.epochSecondToDate(Long.toString((time + offset) / 1000))) + " as date";

        System.out.println(testSQL);
        String expected = deliveryUtilities().getFormatters().iso8601NoClockLong().apply(time);
        String result = db.query(new QueryAllStatement<String>(testSQL) {
            @Override
            public String processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getString("date") : null;
            }
        });
        assertEquals(expected, result);
    }

    @Test
    default void registerDateIsMinimized() {
        executeTransactions(
                new PlayerServerRegisterTransaction(playerUUID, () -> 1000,
                        TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME)
                , new Transaction() {
                    @Override
                    protected void performOperations() {
                        execute("UPDATE " + UserInfoTable.TABLE_NAME + " SET " + UserInfoTable.REGISTERED + "=0" + WHERE + UserInfoTable.USER_UUID + "='" + playerUUID + "'");
                    }
                }
        );

        // Check test assumptions
        Map<UUID, Long> registerDates = db().query(UserInfoQueries.fetchRegisterDates(0L, System.currentTimeMillis(), serverUUID()));
        assertEquals(0L, registerDates.get(playerUUID));
        Optional<BaseUser> baseUser = db().query(BaseUserQueries.fetchBaseUserOfPlayer(playerUUID));
        assertEquals(1000L, baseUser.isPresent() ? baseUser.get().getRegistered() : null);

        RegisterDateMinimizationPatch testedPatch = new RegisterDateMinimizationPatch();
        executeTransactions(testedPatch);

        // Test expected result
        Optional<BaseUser> updatedBaseUser = db().query(BaseUserQueries.fetchBaseUserOfPlayer(playerUUID));
        assertEquals(0L, updatedBaseUser.isPresent() ? updatedBaseUser.get().getRegistered() : null);
        assertTrue(testedPatch.isApplied());
    }

    @Test
    default void serverTablePlayersQueryQueriesAtLeastOnePlayer() {
        db().executeTransaction(new WorldNameStoreTransaction(serverUUID(), worlds[0]));
        db().executeTransaction(new WorldNameStoreTransaction(serverUUID(), worlds[1]));
        db().executeTransaction(new PlayerServerRegisterTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new PlayerServerRegisterTransaction(player2UUID, RandomData::randomTime,
                TestConstants.PLAYER_TWO_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new SessionEndTransaction(RandomData.randomSession(serverUUID(), worlds, playerUUID, player2UUID)));

        List<TablePlayer> result = db().query(new ServerTablePlayersQuery(serverUUID(), System.currentTimeMillis(), 10L, 1));
        assertEquals(1, result.size(), () -> "Incorrect query result: " + result);
        assertNotEquals(Collections.emptyList(), result);
    }

    @Test
    default void networkTablePlayersQueryQueriesAtLeastOnePlayer() {
        db().executeTransaction(new WorldNameStoreTransaction(serverUUID(), worlds[0]));
        db().executeTransaction(new WorldNameStoreTransaction(serverUUID(), worlds[1]));
        db().executeTransaction(new PlayerServerRegisterTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new PlayerServerRegisterTransaction(player2UUID, RandomData::randomTime,
                TestConstants.PLAYER_TWO_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new SessionEndTransaction(RandomData.randomSession(serverUUID(), worlds, playerUUID, player2UUID)));

        List<TablePlayer> result = db().query(new NetworkTablePlayersQuery(System.currentTimeMillis(), 10L, 1));
        assertEquals(1, result.size(), () -> "Incorrect query result: " + result);
    }
}
