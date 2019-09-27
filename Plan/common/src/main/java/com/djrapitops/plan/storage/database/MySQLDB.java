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

import com.djrapitops.plan.exceptions.database.DBInitException;
import com.djrapitops.plan.exceptions.database.DBOpException;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.PluginLang;
import com.djrapitops.plugin.api.Check;
import com.djrapitops.plugin.benchmarking.Timings;
import com.djrapitops.plugin.logging.L;
import com.djrapitops.plugin.logging.console.PluginLogger;
import com.djrapitops.plugin.logging.error.ErrorHandler;
import com.djrapitops.plugin.task.RunnableFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Rsl1122
 */
@Singleton
public class MySQLDB extends SQLDB {

    private static int increment = 1;

    protected DataSource dataSource;

    @Inject
    public MySQLDB(
            Locale locale,
            PlanConfig config,
            Lazy<ServerInfo> serverInfo,
            RunnableFactory runnableFactory,
            PluginLogger pluginLogger,
            Timings timings,
            ErrorHandler errorHandler
    ) {
        super(() -> serverInfo.get().getServerUUID(), locale, config, runnableFactory, pluginLogger, errorHandler);
    }

    private static synchronized void increment() {
        increment++;
    }

    @Override
    public DBType getType() {
        return DBType.MYSQL;
    }

    private void loadMySQLDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            errorHandler.log(L.CRITICAL, this.getClass(), e);
        }
    }

    /**
     * Setups the {@link HikariDataSource}
     */
    @Override
    public void setupDataSource() {
        try {
            if (Check.isVelocityAvailable()) loadMySQLDriver();

            HikariConfig hikariConfig = new HikariConfig();

            String host = config.get(DatabaseSettings.MYSQL_HOST);
            String port = config.get(DatabaseSettings.MYSQL_PORT);
            String database = config.get(DatabaseSettings.MYSQL_DATABASE);
            String launchOptions = config.get(DatabaseSettings.MYSQL_LAUNCH_OPTIONS);
            // REGEX: match "?", match "word=word&" *-times, match "word=word"
            if (launchOptions.isEmpty() || !launchOptions.matches("\\?((\\w*=\\w*)&)*(\\w*=\\w*)")) {
                launchOptions = "?rewriteBatchedStatements=true&useSSL=false";
                logger.error(locale.getString(PluginLang.DB_MYSQL_LAUNCH_OPTIONS_FAIL, launchOptions));
            }
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + launchOptions);

            String username = config.get(DatabaseSettings.MYSQL_USER);
            String password = config.get(DatabaseSettings.MYSQL_PASS);

            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            hikariConfig.setPoolName("Plan Connection Pool-" + increment);
            increment();

            hikariConfig.setAutoCommit(true);
            hikariConfig.setMaximumPoolSize(8);
            hikariConfig.setMaxLifetime(TimeUnit.MINUTES.toMillis(25L));
            hikariConfig.setLeakDetectionThreshold(TimeUnit.MINUTES.toMillis(10L));

            this.dataSource = new HikariDataSource(hikariConfig);
        } catch (HikariPool.PoolInitializationException e) {
            throw new DBInitException("Failed to set-up HikariCP Datasource: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        if (!connection.isValid(5)) {
            connection.close();
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
            try {
                setupDataSource();
                // get new connection after restarting pool
                connection = dataSource.getConnection();
            } catch (DBInitException e) {
                throw new DBOpException("Failed to restart DataSource after a connection was invalid: " + e.getMessage(), e);
            }
        }
        if (connection.getAutoCommit()) connection.setAutoCommit(false);
        setTimezoneToUTC(connection);
        return connection;
    }

    private void setTimezoneToUTC(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set time_zone = '+00:00'");
        }
    }

    @Override
    public void close() {
        super.close();

        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Override
    public void returnToPool(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            errorHandler.log(L.ERROR, this.getClass(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MySQLDB mySQLDB = (MySQLDB) o;
        return Objects.equals(dataSource, mySQLDB.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSource);
    }
}
