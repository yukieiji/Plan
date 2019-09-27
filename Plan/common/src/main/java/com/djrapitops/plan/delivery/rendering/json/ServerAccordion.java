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
package com.djrapitops.plan.delivery.rendering.json;

import com.djrapitops.plan.delivery.domain.container.DataContainer;
import com.djrapitops.plan.delivery.domain.container.PerServerContainer;
import com.djrapitops.plan.delivery.domain.container.PlayerContainer;
import com.djrapitops.plan.delivery.domain.keys.PerServerKeys;
import com.djrapitops.plan.delivery.domain.keys.PlayerKeys;
import com.djrapitops.plan.delivery.domain.mutators.SessionsMutator;
import com.djrapitops.plan.delivery.formatting.Formatter;
import com.djrapitops.plan.delivery.rendering.json.graphs.Graphs;
import com.djrapitops.plan.delivery.rendering.json.graphs.pie.WorldPie;
import com.djrapitops.plan.gathering.domain.WorldTimes;

import java.util.*;

/**
 * Utility for creating JSON for Server Accordion
 *
 * @author Rsl1122
 */
public class ServerAccordion {

    private final Map<UUID, String> serverNames;
    private final PerServerContainer perServer;
    private final String unknown;

    private final Graphs graphs;
    private final Formatter<Long> year;
    private final Formatter<Long> timeAmount;

    public ServerAccordion(
            PlayerContainer container, Map<UUID, String> serverNames,
            Graphs graphs,
            Formatter<Long> year,
            Formatter<Long> timeAmount,
            String unknown
    ) {
        this.graphs = graphs;
        this.year = year;
        this.timeAmount = timeAmount;

        this.serverNames = serverNames;
        perServer = container.getValue(PlayerKeys.PER_SERVER)
                .orElse(new PerServerContainer());
        this.unknown = unknown;
    }

    public List<Map<String, Object>> asMaps() {
        List<Map<String, Object>> servers = new ArrayList<>();

        for (Map.Entry<UUID, DataContainer> entry : perServer.entrySet()) {
            UUID serverUUID = entry.getKey();
            DataContainer perServer = entry.getValue();
            Map<String, Object> server = new HashMap<>();

            String serverName = serverNames.getOrDefault(serverUUID, unknown);
            WorldTimes worldTimes = perServer.getValue(PerServerKeys.WORLD_TIMES).orElse(new WorldTimes());
            SessionsMutator sessionsMutator = SessionsMutator.forContainer(perServer);

            server.put("server_name", serverName);

            server.put("banned", perServer.getValue(PerServerKeys.BANNED).orElse(false));
            server.put("operator", perServer.getValue(PerServerKeys.OPERATOR).orElse(false));
            server.put("registered", year.apply(perServer.getValue(PerServerKeys.REGISTERED).orElse(0L)));
            server.put("last_seen", year.apply(sessionsMutator.toLastSeen()));

            server.put("session_count", sessionsMutator.count());
            server.put("playtime", timeAmount.apply(sessionsMutator.toPlaytime()));
            server.put("afk_time", timeAmount.apply(sessionsMutator.toAfkTime()));
            server.put("session_median", timeAmount.apply(sessionsMutator.toMedianSessionLength()));
            server.put("longest_session_length", timeAmount.apply(sessionsMutator.toLongestSessionLength()));

            server.put("mob_kills", sessionsMutator.toMobKillCount());
            server.put("player_kills", sessionsMutator.toPlayerKillCount());
            server.put("deaths", sessionsMutator.toDeathCount());

            WorldPie worldPie = graphs.pie().worldPie(worldTimes);
            server.put("world_pie_series", worldPie.getSlices());
            server.put("gm_series", worldPie.toHighChartsDrillDownMaps());

            servers.add(server);
        }
        return servers;
    }
}
