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
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.objects.GeoInfoQueries;
import com.djrapitops.plan.utilities.dev.Untrusted;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class GeolocationsFilter extends MultiOptionFilter {

    private final DBSystem dbSystem;

    @Inject
    public GeolocationsFilter(DBSystem dbSystem) {this.dbSystem = dbSystem;}

    @Override
    public String getKind() {
        return "geolocations";
    }

    @Override
    public Map<String, Object> getOptions() {
        return Collections.singletonMap("options", getSelectionOptions());
    }

    private List<String> getSelectionOptions() {
        return dbSystem.getDatabase().query(GeoInfoQueries.uniqueGeolocations());
    }

    @Override
    public Set<Integer> getMatchingUserIds(@Untrusted InputFilterDto query) {
        return dbSystem.getDatabase().query(GeoInfoQueries.userIdsOfPlayersWithGeolocations(getSelected(query)));
    }
}
