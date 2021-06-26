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
package com.djrapitops.plan.storage.database.transactions.commands;

import com.djrapitops.plan.delivery.domain.auth.User;
import com.djrapitops.plan.storage.database.sql.tables.SecurityTable;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.Transaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Transaction to save a new Plan {@link User} to the database.
 *
 * @author AuroraLS3
 */
public class RegisterWebUserTransaction extends Transaction {

    private final User user;

    public RegisterWebUserTransaction(User user) {
        this.user = user;
    }

    @Override
    protected void performOperations() {
        execute(new ExecStatement(SecurityTable.INSERT_STATEMENT) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, user.getUsername());
                if (user.getLinkedToUUID() == null) {
                    statement.setNull(2, Types.VARCHAR);
                } else {
                    statement.setString(2, user.getLinkedToUUID().toString());
                }
                statement.setString(3, user.getPasswordHash());
                statement.setInt(4, user.getPermissionLevel());
            }
        });
    }
}