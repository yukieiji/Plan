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
package com.djrapitops.plan.delivery.webserver.resolver.json;

import com.djrapitops.plan.delivery.rendering.json.graphs.GraphJSONCreator;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.delivery.webserver.cache.AsyncJSONResolverService;
import com.djrapitops.plan.delivery.webserver.cache.DataID;
import com.djrapitops.plan.delivery.webserver.cache.JSONStorage;
import com.djrapitops.plan.identification.Identifiers;
import com.djrapitops.plan.identification.ServerUUID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Optional;

/**
 * Resolves /v1/graph JSON requests.
 *
 * @author AuroraLS3
 */
@Singleton
public class GraphsJSONResolver implements Resolver {

    private final Identifiers identifiers;
    private final AsyncJSONResolverService jsonResolverService;
    private final GraphJSONCreator graphJSON;

    @Inject
    public GraphsJSONResolver(
            Identifiers identifiers,
            AsyncJSONResolverService jsonResolverService, GraphJSONCreator graphJSON
    ) {
        this.identifiers = identifiers;
        this.jsonResolverService = jsonResolverService;
        this.graphJSON = graphJSON;
    }

    @Override
    public boolean canAccess(Request request) {
        return request.getUser().orElse(new WebUser("")).hasPermission("page.server");
    }

    /**
     * Resolves the request.
     *
     * @param request HTTP request, contains all information necessary to resolve the request.
     * @return JSON response.
     * @throws BadRequestException If 'type' parameter is not defined or supported.
     * @throws BadRequestException If 'server' parameter is not defined or server is not found in database.
     */
    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.of(getResponse(request));
    }

    private Response getResponse(Request request) {
        String type = request.getQuery().get("type")
                .orElseThrow(() -> new BadRequestException("'type' parameter was not defined."));

        DataID dataID = getDataID(type);

        return Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(getGraphJSON(request, dataID).json)
                .build();
    }

    private JSONStorage.StoredJSON getGraphJSON(Request request, DataID dataID) {
        long timestamp = Identifiers.getTimestamp(request);

        JSONStorage.StoredJSON storedJSON;
        if (request.getQuery().get("server").isPresent()) {
            ServerUUID serverUUID = identifiers.getServerUUID(request); // Can throw BadRequestException
            storedJSON = jsonResolverService.resolve(
                    timestamp, dataID, serverUUID,
                    theServerUUID -> generateGraphDataJSONOfType(dataID, theServerUUID)
            );
        } else {
            // Assume network
            storedJSON = jsonResolverService.resolve(
                    timestamp, dataID, () -> generateGraphDataJSONOfType(dataID)
            );
        }
        return storedJSON;
    }

    private DataID getDataID(String type) {
        switch (type) {
            case "performance":
                return DataID.GRAPH_PERFORMANCE;
            case "optimizedPerformance":
                return DataID.GRAPH_OPTIMIZED_PERFORMANCE;
            case "playersOnline":
                return DataID.GRAPH_ONLINE;
            case "uniqueAndNew":
                return DataID.GRAPH_UNIQUE_NEW;
            case "hourlyUniqueAndNew":
                return DataID.GRAPH_HOURLY_UNIQUE_NEW;
            case "serverCalendar":
                return DataID.GRAPH_CALENDAR;
            case "worldPie":
                return DataID.GRAPH_WORLD_PIE;
            case "activity":
                return DataID.GRAPH_ACTIVITY;
            case "geolocation":
                return DataID.GRAPH_WORLD_MAP;
            case "aggregatedPing":
                return DataID.GRAPH_PING;
            case "punchCard":
                return DataID.GRAPH_PUNCHCARD;
            case "serverPie":
                return DataID.GRAPH_SERVER_PIE;
            case "joinAddressPie":
                return DataID.GRAPH_HOSTNAME_PIE;
            default:
                throw new BadRequestException("unknown 'type' parameter.");
        }
    }

    private Object generateGraphDataJSONOfType(DataID id, ServerUUID serverUUID) {
        switch (id) {
            case GRAPH_PERFORMANCE:
                return graphJSON.performanceGraphJSON(serverUUID);
            case GRAPH_OPTIMIZED_PERFORMANCE:
                return graphJSON.optimizedPerformanceGraphJSON(serverUUID);
            case GRAPH_ONLINE:
                return graphJSON.playersOnlineGraph(serverUUID);
            case GRAPH_UNIQUE_NEW:
                return graphJSON.uniqueAndNewGraphJSON(serverUUID);
            case GRAPH_HOURLY_UNIQUE_NEW:
                return graphJSON.hourlyUniqueAndNewGraphJSON(serverUUID);
            case GRAPH_CALENDAR:
                return graphJSON.serverCalendarJSON(serverUUID);
            case GRAPH_WORLD_PIE:
                return graphJSON.serverWorldPieJSONAsMap(serverUUID);
            case GRAPH_HOSTNAME_PIE:
                return graphJSON.playerHostnamePieJSONAsMap(serverUUID);
            case GRAPH_ACTIVITY:
                return graphJSON.activityGraphsJSONAsMap(serverUUID);
            case GRAPH_WORLD_MAP:
                return graphJSON.geolocationGraphsJSONAsMap(serverUUID);
            case GRAPH_PING:
                return graphJSON.pingGraphsJSON(serverUUID);
            case GRAPH_PUNCHCARD:
                return graphJSON.punchCardJSONAsMap(serverUUID);
            default:
                return Collections.singletonMap("error", "Undefined ID: " + id.name());
        }
    }

    private Object generateGraphDataJSONOfType(DataID id) {
        switch (id) {
            case GRAPH_ACTIVITY:
                return graphJSON.activityGraphsJSONAsMap();
            case GRAPH_UNIQUE_NEW:
                return graphJSON.uniqueAndNewGraphJSON();
            case GRAPH_HOURLY_UNIQUE_NEW:
                return graphJSON.hourlyUniqueAndNewGraphJSON();
            case GRAPH_SERVER_PIE:
                return graphJSON.serverPreferencePieJSONAsMap();
            case GRAPH_HOSTNAME_PIE:
                return graphJSON.playerHostnamePieJSONAsMap();
            case GRAPH_WORLD_MAP:
                return graphJSON.geolocationGraphsJSONAsMap();
            default:
                return Collections.singletonMap("error", "Undefined ID: " + id.name());
        }
    }
}