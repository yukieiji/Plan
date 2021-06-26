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
package com.djrapitops.plan.extension.implementation.storage.transactions;

import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.sql.tables.ExtensionIconTable;
import com.djrapitops.plan.storage.database.sql.tables.ExtensionPluginTable;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.Executable;
import com.djrapitops.plan.storage.database.transactions.ThrowawayTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.djrapitops.plan.storage.database.sql.building.Sql.AND;
import static com.djrapitops.plan.storage.database.sql.building.Sql.WHERE;

/**
 * Transaction to update command usage information in the database.
 *
 * @author AuroraLS3
 */
public class StorePluginTransaction extends ThrowawayTransaction {

    private final String pluginName;
    private final long time;
    private final ServerUUID serverUUID;
    private final Icon icon;

    public StorePluginTransaction(String pluginName, long time, ServerUUID serverUUID, Icon icon) {
        this.pluginName = pluginName;
        this.time = time;
        this.serverUUID = serverUUID;
        this.icon = icon;
    }

    @Override
    protected void performOperations() {
        execute(storePlugin());
    }

    private Executable storePlugin() {
        return connection -> {
            if (!updatePlugin().execute(connection)) {
                return insertPlugin().execute(connection);
            }
            return false;
        };
    }

    private Executable updatePlugin() {
        String sql = "UPDATE " + ExtensionPluginTable.TABLE_NAME +
                " SET " +
                ExtensionPluginTable.LAST_UPDATED + "=?," +
                ExtensionPluginTable.ICON_ID + "=" + ExtensionIconTable.STATEMENT_SELECT_ICON_ID +
                WHERE + ExtensionPluginTable.PLUGIN_NAME + "=?" +
                AND + ExtensionPluginTable.SERVER_UUID + "=?";
        return new ExecStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, time);
                ExtensionIconTable.set3IconValuesToStatement(statement, 2, icon);
                statement.setString(5, pluginName);
                statement.setString(6, serverUUID.toString());
            }
        };
    }

    private Executable insertPlugin() {
        String sql = "INSERT INTO " + ExtensionPluginTable.TABLE_NAME + "(" +
                ExtensionPluginTable.PLUGIN_NAME + "," +
                ExtensionPluginTable.LAST_UPDATED + "," +
                ExtensionPluginTable.SERVER_UUID + "," +
                ExtensionPluginTable.ICON_ID +
                ") VALUES (?,?,?," + ExtensionIconTable.STATEMENT_SELECT_ICON_ID + ")";
        return new ExecStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, pluginName);
                statement.setLong(2, time);
                statement.setString(3, serverUUID.toString());
                ExtensionIconTable.set3IconValuesToStatement(statement, 4, icon);
            }
        };
    }
}