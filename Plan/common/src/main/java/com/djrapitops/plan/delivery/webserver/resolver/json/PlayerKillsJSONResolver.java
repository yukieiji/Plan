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

import com.djrapitops.plan.delivery.rendering.json.JSONFactory;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
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
 * Resolves /v1/kills JSON requests.
 *
 * @author AuroraLS3
 */
@Singleton
public class PlayerKillsJSONResolver implements Resolver {

    private final Identifiers identifiers;
    private final AsyncJSONResolverService jsonResolverService;
    private final JSONFactory jsonFactory;

    @Inject
    public PlayerKillsJSONResolver(
            Identifiers identifiers,
            AsyncJSONResolverService jsonResolverService,
            JSONFactory jsonFactory
    ) {
        this.identifiers = identifiers;
        this.jsonResolverService = jsonResolverService;
        this.jsonFactory = jsonFactory;
    }

    @Override
    public boolean canAccess(Request request) {
        return request.getUser().orElse(new WebUser("")).hasPermission("page.server");
    }

    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.of(getResponse(request));
    }

    private Response getResponse(Request request) {
        ServerUUID serverUUID = identifiers.getServerUUID(request);
        long timestamp = Identifiers.getTimestamp(request);
        JSONStorage.StoredJSON storedJSON = jsonResolverService.resolve(timestamp, DataID.KILLS, serverUUID,
                theUUID -> Collections.singletonMap("player_kills", jsonFactory.serverPlayerKillsAsJSONMap(theUUID))
        );
        return Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(storedJSON.json)
                .build();
    }
}