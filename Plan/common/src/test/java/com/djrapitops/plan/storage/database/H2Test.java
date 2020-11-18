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

import com.djrapitops.plan.PlanSystem;
import com.djrapitops.plan.delivery.DeliveryUtilities;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.storage.database.queries.*;
import com.djrapitops.plan.storage.database.transactions.StoreServerInformationTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemoveEverythingTransaction;
import com.djrapitops.plan.storage.database.transactions.init.CreateTablesTransaction;
import com.djrapitops.plan.storage.database.transactions.patches.Patch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import utilities.DBPreparer;
import utilities.RandomData;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests for the H2 database.
 *
 * @author Rsl1122
 * @see DatabaseTest
 * @see ExtensionsDatabaseTest
 */
@ExtendWith(MockitoExtension.class)
public class H2Test implements DatabaseTest,
        DatabaseBackupTest,
        //ExtensionsDatabaseTest, TODO Test hangs forever for some reason, investigate later.
        ActivityIndexQueriesTest,
        GeolocationQueriesTest,
        NicknameQueriesTest,
        PingQueriesTest,
        SessionQueriesTest,
        ServerQueriesTest,
        TPSQueriesTest,
        UserInfoQueriesTest,
        WebUserQueriesTest {

    private static final int TEST_PORT_NUMBER = RandomData.randomInt(9005, 9500);

    private static Database database;
    private static DatabaseTestComponent component;
    private static DBPreparer preparer;

    @BeforeAll
    static void setupDatabase(@TempDir Path temp) {
        component = DaggerDatabaseTestComponent.builder()
                .bindTemporaryDirectory(temp)
                .build();
        preparer = new DBPreparer(component, TEST_PORT_NUMBER);
        database = preparer.prepareH2()
                .orElseThrow(IllegalStateException::new);
    }

    @BeforeEach
    void setUp() {
        db().executeTransaction(new Patch() {
            @Override
            public boolean hasBeenApplied() {
                return false;
            }

            @Override
            public void applyPatch() {
                dropTable("plan_world_times");
                dropTable("plan_kills");
                dropTable("plan_sessions");
                dropTable("plan_worlds");
                dropTable("plan_users");
            }
        });
        db().executeTransaction(new CreateTablesTransaction());
        db().executeTransaction(new RemoveEverythingTransaction());

        db().executeTransaction(new StoreServerInformationTransaction(new Server(serverUUID(), "ServerName", "")));
        assertEquals(serverUUID(), ((SQLDB) db()).getServerUUIDSupplier().get());
    }

    @AfterAll
    static void disableSystem() {
        if (database != null) {
            database.close();
            System.out.println("Database state after close: " + database.getState().name());
        }
        preparer.tearDown();
    }

    @Override
    public Database db() {
        return database;
    }

    @Override
    public UUID serverUUID() {
        return component.serverInfo().getServerUUID();
    }

    @Override
    public PlanConfig config() {
        return component.config();
    }

    @Override
    public DBSystem dbSystem() {
        return component.dbSystem();
    }

    @Override
    public ServerInfo serverInfo() {
        return component.serverInfo();
    }

    @Override
    public DeliveryUtilities deliveryUtilities() {
        return component.deliveryUtilities();
    }

    @Override
    public PlanSystem system() {
        PlanSystem mockSystem = Mockito.mock(PlanSystem.class);
        when(mockSystem.getPlanFiles()).thenReturn(component.files());
        return mockSystem;
    }
}
