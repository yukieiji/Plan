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

import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.FilterLang;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.filter.CompleteSetException;
import com.djrapitops.plan.storage.database.queries.filter.SpecifiedFilterInformation;
import com.djrapitops.plan.storage.database.queries.objects.UserInfoQueries;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class BannedFilter extends MultiOptionFilter {

    private final DBSystem dbSystem;
    private final Locale locale;

    @Inject
    public BannedFilter(
            DBSystem dbSystem,
            Locale locale
    ) {
        this.dbSystem = dbSystem;
        this.locale = locale;
    }

    @Override
    public String getKind() {
        return "banned";
    }

    private String[] getOptionsArray() {
        return new String[]{locale.getString(FilterLang.BANNED), locale.getString(FilterLang.NOT_BANNED)};
    }

    @Override
    public Map<String, Object> getOptions() {
        return Collections.singletonMap("options", getOptionsArray());
    }

    @Override
    public Set<UUID> getMatchingUUIDs(SpecifiedFilterInformation query) {
        List<String> selected = getSelected(query);
        Set<UUID> uuids = new HashSet<>();
        String[] options = getOptionsArray();

        boolean includeBanned = selected.contains(options[0]);
        boolean includeNotBanned = selected.contains(options[1]);

        if (includeBanned && includeNotBanned) throw new CompleteSetException(); // Full set, no need for query
        if (includeBanned) uuids.addAll(dbSystem.getDatabase().query(UserInfoQueries.uuidsOfBanned()));
        if (includeNotBanned) uuids.addAll(dbSystem.getDatabase().query(UserInfoQueries.uuidsOfNotBanned()));
        return uuids;
    }
}
