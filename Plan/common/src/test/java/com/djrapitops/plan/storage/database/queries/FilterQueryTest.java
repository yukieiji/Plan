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
package com.djrapitops.plan.storage.database.queries;

import com.djrapitops.plan.delivery.domain.datatransfer.InputFilterDto;
import com.djrapitops.plan.settings.locale.lang.FilterLang;
import com.djrapitops.plan.settings.locale.lang.HtmlLang;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.queries.filter.Filter;
import com.djrapitops.plan.storage.database.queries.filter.filters.PluginBooleanGroupFilter;
import com.djrapitops.plan.utilities.java.Maps;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import utilities.TestConstants;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public interface FilterQueryTest extends DatabaseTestPreparer {

    @TestFactory
    default Collection<DynamicTest> filterOptionsQueriesTests() {
        Map<String, Filter> filters = queryFilters().getFilters();

        return filters.entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest("Filter " + entry.getKey() + " gets options",
                        () -> assertNotNull(entry.getValue().getOptions())))
                .collect(Collectors.toList());
    }

    @TestFactory
    default Collection<DynamicTest> filterGetsResults() {
        Map<String, Map> filtersAndOptions = Maps.builder(String.class, Map.class)
                .put("playedBetween", Maps.builder(String.class, String.class)
                        .put("afterDate", "10/10/10")
                        .put("afterTime", "00:00")
                        .put("beforeDate", "10/10/10")
                        .put("beforeTime", "00:00")
                        .build())
                .put("registeredBetween", Maps.builder(String.class, String.class)
                        .put("afterDate", "10/10/10")
                        .put("afterTime", "00:00")
                        .put("beforeDate", "10/10/10")
                        .put("beforeTime", "00:00")
                        .build())
                .put("operators", Map.of("selected", "[" + FilterLang.OPERATORS.getDefault() + "]"))
                .put("playedOnServer", Map.of("selected", "[" + TestConstants.SERVER_NAME + "]"))
                .put("activityIndexNow", Map.of("selected", "[" + HtmlLang.INDEX_ACTIVE.getDefault() + "]"))
                .put("banned", Map.of("selected", "[" + FilterLang.BANNED.getDefault() + "]"))
                .put("geolocations", Map.of("selected", "[Finland]"))
                .put("joinAddresses", Map.of("selected", "[unknown]"))
                .put("pluginsBooleanGroups", Map.of("selected", "[\"" + new PluginBooleanGroupFilter.PluginBooleanOption(TestConstants.SERVER_NAME, "TestExtension", "isJailed").format() + ": false\"]"))
                .build();

        List<DynamicTest> tests = new ArrayList<>();

        Set<String> testedKinds = new HashSet<>();

        for (Map.Entry<String, Map> entry : filtersAndOptions.entrySet()) {
            String kind = entry.getKey();
            Map<String, String> parameters = (Map<String, String>) entry.getValue();

            tests.add(DynamicTest.dynamicTest("Filter " + kind + " gets results", () -> {
                Filter filter = getFilter(kind);
                InputFilterDto input = new InputFilterDto(kind, parameters);

                testFilter(filter, input);
            }));

            testedKinds.add(kind);
        }

        assertAll(queryFilters().getFilters().keySet().stream()
                .map(kind -> () -> assertTrue(testedKinds.contains(kind), () -> "Incorrect test setup: Filter '" + kind + "' is not being tested")));

        return tests;
    }

    private Filter getFilter(String kind) {
        return queryFilters().getFilter(kind)
                .orElseThrow(() -> new AssertionError("Unknown filter '" + kind + "'"));
    }

    private void testFilter(Filter filter, InputFilterDto input) {
        Set<String> setParameters = input.getSetParameters();
        assertAll(Arrays.stream(filter.getExpectedParameters())
                .map(parameter -> () -> assertTrue(setParameters.contains(parameter), () -> "Incorrect test setup: Parameter '" + parameter + "' was not set for filter " + filter.getKind())));

        Set<Integer> matchingUserIds = filter.getMatchingUserIds(input);
        assertNotNull(matchingUserIds);
    }
}
