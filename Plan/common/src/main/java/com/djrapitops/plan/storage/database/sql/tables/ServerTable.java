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

import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.sql.building.CreateTableBuilder;
import com.djrapitops.plan.storage.database.sql.building.Insert;
import com.djrapitops.plan.storage.database.sql.building.Sql;
import com.djrapitops.plan.storage.database.sql.building.Update;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Table information about 'plan_servers'.
 *
 * @author AuroraLS3
 * @see Server
 */
public class ServerTable {

    public static final String TABLE_NAME = "plan_servers";

    public static final String SERVER_ID = "id";
    public static final String SERVER_UUID = "uuid";
    public static final String NAME = "name";
    public static final String WEB_ADDRESS = "web_address";
    public static final String INSTALLED = "is_installed";
    public static final String PROXY = "is_proxy";
    @Deprecated
    public static final String MAX_PLAYERS = "max_players";

    public static final String INSERT_STATEMENT = Insert.values(TABLE_NAME,
            SERVER_UUID, NAME,
            WEB_ADDRESS, INSTALLED, PROXY);

    public static final String UPDATE_STATEMENT = Update.values(TABLE_NAME,
            SERVER_UUID,
            NAME,
            WEB_ADDRESS,
            INSTALLED,
            PROXY)
            .where(SERVER_UUID + "=?")
            .toString();

    public static final String STATEMENT_SELECT_SERVER_ID =
            '(' + SELECT + TABLE_NAME + '.' + SERVER_ID +
                    FROM + TABLE_NAME +
                    WHERE + TABLE_NAME + '.' + SERVER_UUID + "=? LIMIT 1)";

    private ServerTable() {
        /* Static information class */
    }

    public static String createTableSQL(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(SERVER_ID, Sql.INT).primaryKey()
                .column(SERVER_UUID, Sql.varchar(36)).notNull().unique()
                .column(NAME, Sql.varchar(100))
                .column(WEB_ADDRESS, Sql.varchar(100))
                .column(INSTALLED, Sql.BOOL).notNull().defaultValue(true)
                .column(PROXY, Sql.BOOL).notNull().defaultValue(false)
                .column(MAX_PLAYERS, Sql.INT).notNull().defaultValue("-1")
                .toString();
    }
}
