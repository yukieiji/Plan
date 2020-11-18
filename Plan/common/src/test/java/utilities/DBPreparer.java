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

import com.djrapitops.plan.PlanSystem;
import com.djrapitops.plan.SubSystem;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.config.paths.WebserverSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.SQLDB;
import com.djrapitops.plan.storage.database.transactions.Transaction;
import com.djrapitops.plugin.utilities.Format;
import com.djrapitops.plugin.utilities.Verify;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Optional;

public class DBPreparer {

    private final Dependencies dependencies;
    private final int testPortNumber;

    public DBPreparer(PlanSystem system, int testPortNumber) {
        this(new PlanSystemAsDependencies(system), testPortNumber);
    }

    public DBPreparer(Dependencies dependencies, int testPortNumber) {
        this.dependencies = dependencies;
        this.testPortNumber = testPortNumber;
    }

    public Optional<Database> prepareSQLite() {
        String dbName = DBType.SQLITE.getName();
        return Optional.of(prepareDBByName(dbName));
    }

    public Optional<Database> prepareH2() {
        String dbName = DBType.H2.getName();
        return Optional.of(prepareDBByName(dbName));
    }

    private SQLDB prepareDBByName(String dbName) {
        PlanConfig config = dependencies.config();
        config.set(WebserverSettings.PORT, testPortNumber);
        config.set(DatabaseSettings.TYPE, dbName);
        dependencies.enable();

        DBSystem dbSystem = dependencies.dbSystem();
        SQLDB db = (SQLDB) dbSystem.getActiveDatabaseByName(dbName);
        db.setTransactionExecutorServiceProvider(MoreExecutors::newDirectExecutorService);
        db.init();
        return db;
    }

    public Optional<String> setUpMySQLSettings(PlanConfig config) {
        String database = System.getenv(CIProperties.MYSQL_DATABASE);
        String user = System.getenv(CIProperties.MYSQL_USER);
        String pass = System.getenv(CIProperties.MYSQL_PASS);
        String port = System.getenv(CIProperties.MYSQL_PORT);
        if (Verify.containsNull(database, user)) {
            return Optional.empty();
        }

        // Attempt to Prevent SQL Injection with Environment variable.
        String formattedDatabase = new Format(database)
                .removeSymbols()
                .toString();

        String dbName = DBType.MYSQL.getName();

        config.set(DatabaseSettings.MYSQL_DATABASE, formattedDatabase);
        config.set(DatabaseSettings.MYSQL_USER, user);
        config.set(DatabaseSettings.MYSQL_PASS, pass != null ? pass : "");
        config.set(DatabaseSettings.MYSQL_HOST, "127.0.0.1");
        config.set(DatabaseSettings.MYSQL_PORT, port != null ? port : "3306");
        config.set(DatabaseSettings.TYPE, dbName);
        return Optional.of(formattedDatabase);
    }

    public Optional<Database> prepareMySQL() {
        PlanConfig config = dependencies.config();
        Optional<String> formattedDB = setUpMySQLSettings(config);
        if (formattedDB.isPresent()) {
            String formattedDatabase = formattedDB.get();
            SQLDB mysql = prepareDBByName(DBType.MYSQL.getName());
            mysql.executeTransaction(new Transaction() {
                @Override
                protected void performOperations() {
                    execute("DROP DATABASE " + formattedDatabase);
                    execute("CREATE DATABASE " + formattedDatabase);
                    execute("USE " + formattedDatabase);
                }
            });
            return Optional.of(mysql);
        }
        return Optional.empty();
    }

    public void tearDown() {
        dependencies.disable();
    }

    public interface Dependencies extends SubSystem {
        PlanConfig config();

        DBSystem dbSystem();
    }

    @Deprecated
    static class PlanSystemAsDependencies implements Dependencies {
        private final PlanSystem system;

        PlanSystemAsDependencies(PlanSystem system) {
            this.system = system;
        }

        @Override
        public void enable() {
            system.enable();
        }

        @Override
        public void disable() {
            system.disable();
        }

        @Override
        public PlanConfig config() {
            return system.getConfigSystem().getConfig();
        }

        @Override
        public DBSystem dbSystem() {
            return system.getDatabaseSystem();
        }
    }
}
