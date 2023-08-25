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
package com.djrapitops.plan.delivery.export;

import com.djrapitops.plan.delivery.domain.container.PlayerContainer;
import com.djrapitops.plan.delivery.rendering.pages.Page;
import com.djrapitops.plan.delivery.rendering.pages.PageFactory;
import com.djrapitops.plan.delivery.web.ResourceService;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.NotFoundException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resource.WebResource;
import com.djrapitops.plan.delivery.webserver.resolver.json.RootJSONResolver;
import com.djrapitops.plan.exceptions.WebUserAuthException;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.PluginSettings;
import com.djrapitops.plan.settings.theme.Theme;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.queries.PlayerFetchQueries;
import com.djrapitops.plan.storage.database.queries.containers.ContainerFetchQueries;
import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.storage.file.Resource;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles exporting of /player page html, data and resources.
 *
 * @author AuroraLS3
 */
@Singleton
public class PlayerPageExporter extends FileExporter {

    private final PlanFiles files;
    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final PageFactory pageFactory;
    private final RootJSONResolver jsonHandler;
    private final Theme theme;

    @Inject
    public PlayerPageExporter(
            PlanFiles files,
            PlanConfig config,
            DBSystem dbSystem,
            PageFactory pageFactory,
            RootJSONResolver jsonHandler,
            Theme theme
    ) {
        this.files = files;
        this.config = config;
        this.dbSystem = dbSystem;
        this.pageFactory = pageFactory;
        this.jsonHandler = jsonHandler;
        this.theme = theme;
    }

    public static String[] getRedirections(UUID playerUUID) {
        String player = "player/";
        return new String[]{
                player + playerUUID,
                player + playerUUID + "/overview",
                player + playerUUID + "/sessions",
                player + playerUUID + "/pvppve",
                player + playerUUID + "/servers",
        };
    }

    /**
     * Perform export for a player page.
     *
     * @param toDirectory Path to Export directory
     * @param playerUUID  UUID of the player
     * @throws IOException       If a template can not be read from jar/disk or the result written
     * @throws NotFoundException If a file or resource that is being exported can not be found
     */
    public void export(Path toDirectory, UUID playerUUID) throws IOException {
        Database.State dbState = dbSystem.getDatabase().getState();
        if (dbState == Database.State.CLOSED || dbState == Database.State.CLOSING) return;
        if (Boolean.FALSE.equals(dbSystem.getDatabase().query(PlayerFetchQueries.isPlayerRegistered(playerUUID)))) {
            return;
        }

        ExportPaths exportPaths = new ExportPaths();
        exportPaths.put("../network", toRelativePathFromRoot("network"));
        exportPaths.put("../server/", toRelativePathFromRoot("server"));
        exportRequiredResources(exportPaths, toDirectory);

        Path playerDirectory = toDirectory.resolve("player/" + toFileName(playerUUID.toString()));
        exportJSON(exportPaths, playerDirectory, playerUUID);
        exportHtml(exportPaths, playerDirectory, playerUUID);
        exportReactRedirects(toDirectory, playerUUID);
        exportPaths.clear();
    }

    private void exportHtml(ExportPaths exportPaths, Path playerDirectory, UUID playerUUID) throws IOException {
        if (config.isFalse(PluginSettings.LEGACY_FRONTEND)) return;

        Path to = playerDirectory.resolve("index.html");

        try {
            Database db = dbSystem.getDatabase();
            PlayerContainer player = db.query(ContainerFetchQueries.fetchPlayerContainer(playerUUID));
            Page page = pageFactory.playerPage(player);
            export(to, exportPaths.resolveExportPaths(page.toHtml()));
        } catch (IllegalStateException notFound) {
            throw new NotFoundException(notFound.getMessage());
        }
    }

    private void exportReactRedirects(Path toDirectory, UUID playerUUID) throws IOException {
        if (config.isTrue(PluginSettings.LEGACY_FRONTEND)) return;

        exportReactRedirects(toDirectory, files, config, getRedirections(playerUUID));
    }

    private void exportJSON(ExportPaths exportPaths, Path toDirectory, UUID playerUUID) throws IOException {
        exportJSON(exportPaths, toDirectory, "player?player=" + playerUUID);
    }

    private void exportJSON(ExportPaths exportPaths, Path toDirectory, String resource) throws IOException {
        Response response = getJSONResponse(resource)
                .orElseThrow(() -> new NotFoundException(resource + " was not properly exported: no response"));

        String jsonResourceName = toFileName(toJSONResourceName(resource)) + ".json";

        export(toDirectory.resolve(jsonResourceName), response.getBytes());
        exportPaths.put("../v1/player?player=${encodeURIComponent(playerUUID)}", "./" + jsonResourceName);
    }

    private String toJSONResourceName(String resource) {
        return StringUtils.replaceEach(resource, new String[]{"?", "&", "type=", "player="}, new String[]{"-", "_", "", ""});
    }

    private Optional<Response> getJSONResponse(String resource) {
        try {
            return jsonHandler.getResolver().resolve(new Request("GET", "/v1/" + resource, null, Collections.emptyMap()));
        } catch (WebUserAuthException e) {
            // The rest of the exceptions should not be thrown
            throw new IllegalStateException("Unexpected exception thrown: " + e, e);
        }
    }

    private void exportRequiredResources(ExportPaths exportPaths, Path toDirectory) throws IOException {
        if (config.isFalse(PluginSettings.LEGACY_FRONTEND)) return;

        // Style
        exportResources(exportPaths, toDirectory,
                "../img/Flaticon_circle.png",
                "../css/sb-admin-2.css",
                "../css/style.css",
                "../css/noauth.css",
                "../vendor/datatables/datatables.min.js",
                "../vendor/datatables/datatables.min.css",
                "../vendor/highcharts/modules/map.js",
                "../vendor/highcharts/mapdata/world.js",
                "../vendor/highcharts/modules/drilldown.js",
                "../vendor/highcharts/highcharts.js",
                "../vendor/highcharts/modules/no-data-to-display.js",
                "../vendor/fullcalendar/fullcalendar.min.css",
                "../vendor/momentjs/moment.js",
                "../vendor/masonry/masonry.pkgd.min.js",
                "../vendor/fullcalendar/fullcalendar.min.js",
                "../vendor/fontawesome-free/css/all.min.css",
                "../vendor/fontawesome-free/webfonts/fa-brands-400.eot",
                "../vendor/fontawesome-free/webfonts/fa-brands-400.ttf",
                "../vendor/fontawesome-free/webfonts/fa-brands-400.woff",
                "../vendor/fontawesome-free/webfonts/fa-brands-400.woff2",
                "../vendor/fontawesome-free/webfonts/fa-regular-400.eot",
                "../vendor/fontawesome-free/webfonts/fa-regular-400.ttf",
                "../vendor/fontawesome-free/webfonts/fa-regular-400.woff",
                "../vendor/fontawesome-free/webfonts/fa-regular-400.woff2",
                "../vendor/fontawesome-free/webfonts/fa-solid-900.eot",
                "../vendor/fontawesome-free/webfonts/fa-solid-900.ttf",
                "../vendor/fontawesome-free/webfonts/fa-solid-900.woff",
                "../vendor/fontawesome-free/webfonts/fa-solid-900.woff2",
                "../js/sb-admin-2.js",
                "../js/xmlhttprequests.js",
                "../js/color-selector.js",
                "../js/sessionAccordion.js",
                "../js/graphs.js",
                "../js/player-values.js"
        );
    }

    private void exportResources(ExportPaths exportPaths, Path toDirectory, String... resourceNames) throws IOException {
        for (String resourceName : resourceNames) {
            String nonRelativePath = toNonRelativePath(resourceName);
            exportResource(toDirectory, nonRelativePath);
            exportPaths.put(resourceName, toRelativePathFromRoot(nonRelativePath));
        }
    }

    private void exportResource(Path toDirectory, String resourceName) throws IOException {
        WebResource resource = ResourceService.getInstance().getResource("Plan", resourceName,
                () -> files.getResourceFromJar("web/" + resourceName).asWebResource());
        Path to = toDirectory.resolve(resourceName);

        if (resourceName.endsWith(".css") || resourceName.endsWith("color-selector.js")) {
            export(to, theme.replaceThemeColors(resource.asString()));
        } else if (Resource.isTextResource(resourceName)) {
            export(to, resource.asString());
        } else {
            export(to, resource);
        }
    }

    private String toRelativePathFromRoot(String resourceName) {
        // Player html is exported at /player/<uuid>/index.html
        return "../../" + toNonRelativePath(resourceName);
    }

    private String toNonRelativePath(String resourceName) {
        return StringUtils.remove(StringUtils.remove(resourceName, "../"), "./");
    }

}