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
package com.djrapitops.plan.storage.database.transactions.init;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.*;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.ThrowawayTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Transaction that removes outdated plugin's data after configurable threshold.
 *
 * @author AuroraLS3
 */
public class RemoveOldExtensionsTransaction extends ThrowawayTransaction {

    private final long deleteOlder;
    private final ServerUUID serverUUID;

    public RemoveOldExtensionsTransaction(long deleteAfterMs, ServerUUID serverUUID) {
        deleteOlder = System.currentTimeMillis() - deleteAfterMs;
        this.serverUUID = serverUUID;
    }

    @Override
    protected void performOperations() {
        for (Integer providerID : query(inactiveProviderIDsQuery())) {
            removeValues(providerID);
        }
        for (Integer providerID : query(inactiveTableProviderIDsQuery())) {
            removeTableValues(providerID);
        }
        removeProviders();
    }

    private void removeValues(int providerID) {
        for (String table : new String[]{
                ExtensionPlayerValueTable.TABLE_NAME,
                ExtensionServerValueTable.TABLE_NAME,
                ExtensionGroupsTable.TABLE_NAME
        }) {
            execute(new ExecStatement(DELETE_FROM + table + WHERE + "provider_id=?") {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setInt(1, providerID);
                }
            });
        }
    }

    private void removeTableValues(Integer providerID) {
        for (String table : new String[]{
                ExtensionPlayerTableValueTable.TABLE_NAME,
                ExtensionServerTableValueTable.TABLE_NAME
        }) {
            execute(new ExecStatement(DELETE_FROM + table + WHERE + "table_id=?") {
                @Override
                public void prepare(PreparedStatement statement) throws SQLException {
                    statement.setInt(1, providerID);
                }
            });
        }
    }

    private void removeProviders() {
        execute(new ExecStatement(
                DELETE_FROM + ExtensionProviderTable.TABLE_NAME +
                        WHERE + ExtensionProviderTable.PLUGIN_ID +
                        " IN (" +
                        SELECT + ExtensionPluginTable.ID +
                        FROM + ExtensionPluginTable.TABLE_NAME +
                        WHERE + ExtensionPluginTable.LAST_UPDATED + "<?" +
                        AND + ExtensionPluginTable.SERVER_UUID + "=?)"
        ) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, deleteOlder);
                statement.setString(2, serverUUID.toString());
            }
        });
        execute(new ExecStatement(
                DELETE_FROM + ExtensionTableProviderTable.TABLE_NAME +
                        WHERE + ExtensionTableProviderTable.PLUGIN_ID +
                        " IN (" +
                        SELECT + ExtensionPluginTable.ID +
                        FROM + ExtensionPluginTable.TABLE_NAME +
                        WHERE + ExtensionPluginTable.LAST_UPDATED + "<?" +
                        AND + ExtensionPluginTable.SERVER_UUID + "=?)"
        ) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, deleteOlder);
                statement.setString(2, serverUUID.toString());
            }
        });
    }

    private Query<Collection<Integer>> inactiveProviderIDsQuery() {
        String sql = SELECT + "pr." + ExtensionProviderTable.ID +
                FROM + ExtensionProviderTable.TABLE_NAME + " pr" +
                INNER_JOIN + ExtensionPluginTable.TABLE_NAME + " pl on pl." + ExtensionPluginTable.ID + "=pr." + ExtensionProviderTable.PLUGIN_ID +
                WHERE + ExtensionPluginTable.LAST_UPDATED + "<?" +
                AND + ExtensionPluginTable.SERVER_UUID + "=?";
        return new QueryStatement<Collection<Integer>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, deleteOlder);
                statement.setString(2, serverUUID.toString());
            }

            @Override
            public Collection<Integer> processResults(ResultSet set) throws SQLException {
                Collection<Integer> providerIds = new HashSet<>();
                while (set.next()) {
                    providerIds.add(set.getInt(ExtensionProviderTable.ID));
                }
                return providerIds;
            }
        };
    }

    private Query<Collection<Integer>> inactiveTableProviderIDsQuery() {
        String sql = SELECT + "pr." + ExtensionTableProviderTable.ID +
                FROM + ExtensionTableProviderTable.TABLE_NAME + " pr" +
                INNER_JOIN + ExtensionPluginTable.TABLE_NAME + " pl on pl." + ExtensionPluginTable.ID + "=pr." + ExtensionTableProviderTable.PLUGIN_ID +
                WHERE + ExtensionPluginTable.LAST_UPDATED + "<?" +
                AND + ExtensionPluginTable.SERVER_UUID + "=?";
        return new QueryStatement<Collection<Integer>>(sql, 100) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, deleteOlder);
                statement.setString(2, serverUUID.toString());
            }

            @Override
            public Collection<Integer> processResults(ResultSet set) throws SQLException {
                Collection<Integer> providerIds = new HashSet<>();
                while (set.next()) {
                    providerIds.add(set.getInt(ExtensionProviderTable.ID));
                }
                return providerIds;
            }
        };
    }
}