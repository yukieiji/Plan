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
package com.djrapitops.plan.storage.database.queries.filter.filters;

import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.filter.Filter;
import com.djrapitops.plan.storage.database.queries.filter.SpecifiedFilterInformation;
import com.djrapitops.plan.storage.database.queries.objects.UserIdentifierQueries;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;

/**
 * Special filter only used in cases where no filters are specified.
 *
 * @author AuroraLS3
 */
@Singleton
public class AllPlayersFilter implements Filter {

    private final DBSystem dbSystem;

    @Inject
    public AllPlayersFilter(DBSystem dbSystem) {
        this.dbSystem = dbSystem;
    }

    @Override
    public String getKind() {
        return "allPlayers";
    }

    @Override
    public String[] getExpectedParameters() {
        return new String[0];
    }

    @Override
    public Set<UUID> getMatchingUUIDs(SpecifiedFilterInformation query) {
        return dbSystem.getDatabase().query(UserIdentifierQueries.fetchAllPlayerUUIDs());
    }
}
