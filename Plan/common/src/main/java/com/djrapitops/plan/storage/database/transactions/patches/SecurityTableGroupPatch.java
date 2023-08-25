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
package com.djrapitops.plan.storage.database.transactions.patches;

import com.djrapitops.plan.exceptions.database.DBOpException;
import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.sql.tables.SecurityTable;
import com.djrapitops.plan.storage.database.sql.tables.WebGroupTable;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Replaces permission_level with group_id to plan_security table.
 *
 * @author AuroraLS3
 */
public class SecurityTableGroupPatch extends Patch {

    private final String tempTableName;
    private final String tableName;

    public SecurityTableGroupPatch() {
        tableName = SecurityTable.TABLE_NAME;
        tempTableName = "temp_security";
    }

    @Override
    public boolean hasBeenApplied() {
        return hasColumn(tableName, SecurityTable.GROUP_ID)
                && !hasColumn(tableName, "permission_level")
                && !hasTable(tempTableName); // If this table exists the patch has failed to finish.
    }

    @Override
    protected void applyPatch() {
        try {
            tempOldTable();
            dropTable(tableName);
            execute(SecurityTable.createTableSQL(dbType));

            execute("INSERT INTO " + tableName + " (" +
                    SecurityTable.USERNAME + ',' +
                    SecurityTable.LINKED_TO + ',' +
                    SecurityTable.SALT_PASSWORD_HASH + ',' +
                    SecurityTable.GROUP_ID +
                    ") " + SELECT +
                    SecurityTable.USERNAME + ',' +
                    SecurityTable.LINKED_TO + ',' +
                    SecurityTable.SALT_PASSWORD_HASH + ',' +
                    "(" + SELECT + WebGroupTable.ID + FROM + WebGroupTable.TABLE_NAME + WHERE + WebGroupTable.NAME + "=" + (dbType == DBType.SQLITE ? "'legacy_level_' || permission_level" : "CONCAT('legacy_level_', permission_level)") + ")" +
                    FROM + tempTableName
            );

            dropTable(tempTableName);
        } catch (Exception e) {
            throw new DBOpException(SecurityTableGroupPatch.class.getSimpleName() + " failed.", e);
        }
    }


    private void tempOldTable() {
        if (!hasTable(tempTableName)) {
            renameTable(tableName, tempTableName);
        }
    }
}
