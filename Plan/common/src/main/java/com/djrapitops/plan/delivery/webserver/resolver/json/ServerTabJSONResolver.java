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

import com.djrapitops.plan.delivery.rendering.json.ServerTabJSONCreator;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.delivery.webserver.cache.AsyncJSONResolverService;
import com.djrapitops.plan.delivery.webserver.cache.DataID;
import com.djrapitops.plan.identification.Identifiers;
import com.djrapitops.plan.identification.ServerUUID;

import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface wrapper for resolving server specific JSON directly from other methods.
 *
 * @author AuroraLS3
 */
public class ServerTabJSONResolver<T> implements Resolver {

    private final DataID dataID;
    private final Identifiers identifiers;
    private final Function<ServerUUID, T> jsonCreator;
    private final AsyncJSONResolverService asyncJSONResolverService;

    public ServerTabJSONResolver(
            DataID dataID, Identifiers identifiers, ServerTabJSONCreator<T> jsonCreator,
            AsyncJSONResolverService asyncJSONResolverService
    ) {
        this.dataID = dataID;
        this.identifiers = identifiers;
        this.jsonCreator = jsonCreator;
        this.asyncJSONResolverService = asyncJSONResolverService;
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
        ServerUUID serverUUID = identifiers.getServerUUID(request); // Can throw BadRequestException
        return Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(asyncJSONResolverService.resolve(Identifiers.getTimestamp(request), dataID, serverUUID, jsonCreator).json)
                .build();
    }
}