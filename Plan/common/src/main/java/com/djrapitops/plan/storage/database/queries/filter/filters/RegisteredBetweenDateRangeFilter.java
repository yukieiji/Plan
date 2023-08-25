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

import com.djrapitops.plan.delivery.domain.datatransfer.InputFilterDto;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.objects.BaseUserQueries;
import com.djrapitops.plan.storage.database.queries.objects.ServerQueries;
import com.djrapitops.plan.storage.database.queries.objects.UserInfoQueries;
import com.djrapitops.plan.utilities.dev.Untrusted;
import com.google.gson.Gson;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Singleton
public class RegisteredBetweenDateRangeFilter extends DateRangeFilter {

    private final DBSystem dbSystem;

    @Inject
    public RegisteredBetweenDateRangeFilter(DBSystem dbSystem) {
        super(dbSystem);
        this.dbSystem = dbSystem;
    }

    @Override
    public String getKind() {
        return "registeredBetween";
    }

    @Override
    public Set<Integer> getMatchingUserIds(@Untrusted InputFilterDto query) {
        long after = getAfter(query);
        long before = getBefore(query);
        @Untrusted List<String> serverNames = getServerNames(query);
        List<ServerUUID> serverUUIDs = serverNames.isEmpty() ? Collections.emptyList() : dbSystem.getDatabase().query(ServerQueries.fetchServersMatchingIdentifiers(serverNames));
        return dbSystem.getDatabase().query(
                serverUUIDs.isEmpty() ? BaseUserQueries.userIdsOfRegisteredBetween(after, before)
                        : UserInfoQueries.userIdsOfRegisteredBetween(after, before, serverUUIDs)
        );
    }

    private List<String> getServerNames(@Untrusted InputFilterDto query) {
        return query.get("servers")
                .map(serversList -> new Gson().fromJson(serversList, String[].class))
                .map(Arrays::asList)
                .orElseGet(Collections::emptyList);
    }
}
