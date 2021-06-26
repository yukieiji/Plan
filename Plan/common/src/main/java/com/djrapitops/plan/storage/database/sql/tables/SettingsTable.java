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

/**
 * Table information about 'plan_settings'.
 *
 * @author AuroraLS3
 */
public class SettingsTable {

    public static final String TABLE_NAME = "plan_settings";

    public static final String ID = "id";
    public static final String SERVER_UUID = "server_uuid";
    public static final String UPDATED = "updated";
    public static final String CONFIG_CONTENT = "content";

    public static final String INSERT_STATEMENT = "INSERT INTO " + TABLE_NAME + " (" +
            SERVER_UUID + ',' +
            UPDATED + ',' +
            CONFIG_CONTENT + ") VALUES (?,?,?)";
    public static final String UPDATE_STATEMENT = "UPDATE " + TABLE_NAME + " SET " +
            CONFIG_CONTENT + "=?," +
            UPDATED + "=? WHERE " +
            SERVER_UUID + "=? AND " +
            CONFIG_CONTENT + "!=?";

    private SettingsTable() {
        /* Static information class */
    }

    public static String createTableSQL(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(ID, Sql.INT).primaryKey()
                .column(SERVER_UUID, Sql.varchar(39)).notNull().unique()
                .column(UPDATED, Sql.LONG).notNull()
                .column(CONFIG_CONTENT, "TEXT").notNull()
                .toString();
    }
}
