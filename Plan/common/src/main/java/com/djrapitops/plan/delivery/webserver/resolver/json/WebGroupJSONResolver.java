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

import com.djrapitops.plan.delivery.domain.auth.Group;
import com.djrapitops.plan.delivery.domain.auth.GroupList;
import com.djrapitops.plan.delivery.domain.auth.WebPermission;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.objects.WebUserQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Endpoint for getting list of Plan web permission groups.
 *
 * @author AuroraLS3
 */
@Singleton
@Path("/v1/webGroups")
public class WebGroupJSONResolver implements Resolver {

    private final DBSystem dbSystem;

    @Inject
    public WebGroupJSONResolver(DBSystem dbSystem) {
        this.dbSystem = dbSystem;
    }

    @Override
    public boolean canAccess(Request request) {
        return request.getUser().map(user -> user.hasPermission(WebPermission.MANAGE_GROUPS)).orElse(false);
    }

    @GET
    @Operation(
            description = "Get list of web permission groups",
            responses = {
                    @ApiResponse(responseCode = "200", content = @Content(
                            mediaType = MimeType.JSON,
                            schema = @Schema(implementation = GroupList.class))),
            },
            requestBody = @RequestBody(content = @Content(examples = @ExampleObject()))
    )
    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.of(getResponse());
    }

    private Response getResponse() {
        List<String> groupNames = dbSystem.getDatabase().query(WebUserQueries.fetchGroupNames());

        GroupList groupList = new GroupList(groupNames.stream()
                .map(Group::new)
                .collect(Collectors.toList()));
        return Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(groupList)
                .build();
    }
}
