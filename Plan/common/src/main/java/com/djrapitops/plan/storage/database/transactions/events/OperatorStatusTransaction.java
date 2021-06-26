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
package com.djrapitops.plan.storage.database.transactions.events;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.sql.building.Update;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.Executable;
import com.djrapitops.plan.storage.database.transactions.ThrowawayTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Transaction to update a player's operator status.
 *
 * @author AuroraLS3
 */
public class OperatorStatusTransaction extends ThrowawayTransaction {

    private final UUID playerUUID;
    private final ServerUUID serverUUID;
    private final boolean operatorStatus;

    public OperatorStatusTransaction(UUID playerUUID, ServerUUID serverUUID, boolean operatorStatus) {
        this.playerUUID = playerUUID;
        this.serverUUID = serverUUID;
        this.operatorStatus = operatorStatus;
    }

    @Override
    protected void performOperations() {
        execute(updateOperatorStatus());
    }

    private Executable updateOperatorStatus() {
        String sql = Update.values(UserInfoTable.TABLE_NAME, UserInfoTable.OP)
                .where(UserInfoTable.USER_UUID + "=?")
                .and(UserInfoTable.SERVER_UUID + "=?")
                .toString();

        return new ExecStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setBoolean(1, operatorStatus);
                statement.setString(2, playerUUID.toString());
                statement.setString(3, serverUUID.toString());
            }
        };
    }
}