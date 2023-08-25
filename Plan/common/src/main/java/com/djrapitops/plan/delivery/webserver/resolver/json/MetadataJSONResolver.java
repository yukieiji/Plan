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

import com.djrapitops.plan.delivery.rendering.html.Contributors;
import com.djrapitops.plan.delivery.web.resolver.NoAuthResolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DisplaySettings;
import com.djrapitops.plan.settings.config.paths.ProxySettings;
import com.djrapitops.plan.settings.config.paths.WebserverSettings;
import com.djrapitops.plan.settings.theme.Theme;
import com.djrapitops.plan.settings.theme.ThemeVal;
import com.djrapitops.plan.utilities.java.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@Path("/v1/metadata")
public class MetadataJSONResolver implements NoAuthResolver {

    private final String mainCommand;
    private final PlanConfig config;
    private final Theme theme;
    private final ServerInfo serverInfo;

    @Inject
    public MetadataJSONResolver(
            @Named("mainCommandName") String mainCommand,
            PlanConfig config,
            Theme theme,
            ServerInfo serverInfo
    ) {
        this.mainCommand = mainCommand;
        this.config = config;
        // Dagger inject constructor
        this.theme = theme;
        this.serverInfo = serverInfo;
    }

    @GET
    @Operation(
            description = "Get metadata required for displaying Plan React frontend",
            requestBody = @RequestBody(content = @Content(examples = @ExampleObject()))
    )
    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.of(getResponse());
    }

    private Response getResponse() {
        return Response.builder()
                .setJSONContent(Maps.builder(String.class, Object.class)
                        .put("timestamp", System.currentTimeMillis())
                        .put("timeZoneId", config.getTimeZone().getID())
                        .put("timeZoneOffsetHours", config.getTimeZoneOffsetHours())
                        .put("timeZoneOffsetMinutes", config.getTimeZoneOffsetHours() * 60)
                        .put("contributors", Contributors.getContributors())
                        .put("defaultTheme", config.get(DisplaySettings.THEME))
                        .put("gmPieColors", theme.getPieColors(ThemeVal.GRAPH_GM_PIE))
                        .put("playerHeadImageUrl", config.get(DisplaySettings.PLAYER_HEAD_IMG_URL))
                        .put("isProxy", serverInfo.getServer().isProxy())
                        .put("serverName", serverInfo.getServer().getIdentifiableName())
                        .put("serverUUID", serverInfo.getServer().getUuid().toString())
                        .put("networkName", serverInfo.getServer().isProxy() ? config.get(ProxySettings.NETWORK_NAME) : null)
                        .put("mainCommand", mainCommand)
                        .put("refreshBarrierMs", config.get(WebserverSettings.REDUCED_REFRESH_BARRIER))
                        .put("registrationDisabled", config.isTrue(WebserverSettings.DISABLED_REGISTRATION))
                        .build())
                .build();
    }
}
