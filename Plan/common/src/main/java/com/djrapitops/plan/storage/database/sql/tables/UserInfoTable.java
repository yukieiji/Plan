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
package com.djrapitops.plan.storage.database.sql.tables;

import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.sql.building.CreateTableBuilder;
import com.djrapitops.plan.storage.database.sql.building.Sql;
import com.djrapitops.plan.storage.database.transactions.patches.UserInfoOptimizationPatch;
import com.djrapitops.plan.storage.database.transactions.patches.Version10Patch;

/**
 * Table information about 'plan_user_info'.
 * <p>
 * Patches related to this table:
 * {@link Version10Patch}
 * {@link UserInfoOptimizationPatch}
 * {@link com.djrapitops.plan.storage.database.transactions.patches.RegisterDateMinimizationPatch}
 *
 * @author AuroraLS3
 */
public class UserInfoTable {

    public static final String TABLE_NAME = "plan_user_info";

    public static final String ID = "id";
    public static final String USER_UUID = "uuid";
    public static final String SERVER_UUID = "server_uuid";
    public static final String REGISTERED = "registered";
    public static final String OP = "opped";
    public static final String BANNED = "banned";
    public static final String JOIN_ADDRESS = "join_address";

    public static final String INSERT_STATEMENT = "INSERT INTO " + TABLE_NAME + " (" +
            USER_UUID + ',' +
            REGISTERED + ',' +
            SERVER_UUID + ',' +
            BANNED + ',' +
            JOIN_ADDRESS + ',' +
            OP +
            ") VALUES (?, ?, ?, ?, ?, ?)";

    private UserInfoTable() {
        /* Static information class */
    }

    public static String createTableSQL(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(ID, Sql.INT).primaryKey()
                .column(USER_UUID, Sql.varchar(36)).notNull()
                .column(SERVER_UUID, Sql.varchar(36)).notNull()
                .column(JOIN_ADDRESS, Sql.varchar(255))
                .column(REGISTERED, Sql.LONG).notNull()
                .column(OP, Sql.BOOL).notNull().defaultValue(false)
                .column(BANNED, Sql.BOOL).notNull().defaultValue(false)
                .toString();
    }
}
