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
 * Represents plan_access_log table.
 *
 * @see com.djrapitops.plan.storage.database.transactions.patches.RemoveUsernameFromAccessLogPatch
 */
public class AccessLogTable {

    public static final String TABLE_NAME = "plan_access_log";
    public static final String ID = "id";
    public static final String TIME = "time";
    public static final String FROM_IP = "from_ip";
    public static final String REQUEST_METHOD = "request_method";
    public static final String REQUEST_URI = "request_uri";
    public static final String RESPONSE_CODE = "response_code";
    public static final String INSERT_NO_USER = "INSERT INTO " + TABLE_NAME + " (" +
            TIME + ',' + FROM_IP + ',' + REQUEST_METHOD + ',' + REQUEST_URI + ',' + RESPONSE_CODE +
            ") VALUES (?, ?, ?, ?, ?)";

    private AccessLogTable() {
        /* Static constant class */
    }

    public static String createTableSql(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(ID, Sql.INT).primaryKey()
                .column(TIME, Sql.LONG).notNull()
                .column(FROM_IP, Sql.varchar(45)) // Max IPv6 text length 45 chars
                .column(REQUEST_METHOD, Sql.varchar(8)).notNull()
                .column(REQUEST_URI, Sql.TEXT).notNull()
                .column(RESPONSE_CODE, Sql.INT)
                .build();
    }
}
