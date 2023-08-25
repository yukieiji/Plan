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
package com.djrapitops.plan.delivery.rendering.pages;

import com.djrapitops.plan.component.ComponentSvc;
import com.djrapitops.plan.delivery.domain.container.PlayerContainer;
import com.djrapitops.plan.delivery.formatting.Formatters;
import com.djrapitops.plan.delivery.rendering.html.icon.Icon;
import com.djrapitops.plan.delivery.web.ResourceService;
import com.djrapitops.plan.delivery.web.resolver.exception.NotFoundException;
import com.djrapitops.plan.delivery.web.resource.WebResource;
import com.djrapitops.plan.delivery.webserver.Addresses;
import com.djrapitops.plan.delivery.webserver.cache.JSONStorage;
import com.djrapitops.plan.extension.implementation.results.ExtensionData;
import com.djrapitops.plan.extension.implementation.storage.queries.ExtensionPlayerDataQuery;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.PluginSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.theme.Theme;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.queries.objects.ServerQueries;
import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.storage.file.PublicHtmlFiles;
import com.djrapitops.plan.utilities.dev.Untrusted;
import com.djrapitops.plan.version.VersionChecker;
import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Factory for creating different {@link Page} objects.
 *
 * @author AuroraLS3
 */
@Singleton
public class PageFactory {

    private final Lazy<VersionChecker> versionChecker;
    private final Lazy<PlanFiles> files;
    private final Lazy<PublicHtmlFiles> publicHtmlFiles;
    private final Lazy<PlanConfig> config;
    private final Lazy<Theme> theme;
    private final Lazy<DBSystem> dbSystem;
    private final Lazy<ServerInfo> serverInfo;
    private final Lazy<JSONStorage> jsonStorage;
    private final Lazy<Formatters> formatters;
    private final Lazy<Locale> locale;
    private final Lazy<ComponentSvc> componentService;
    private final Lazy<Addresses> addresses;

    @Inject
    public PageFactory(
            Lazy<VersionChecker> versionChecker,
            Lazy<PlanFiles> files,
            Lazy<PublicHtmlFiles> publicHtmlFiles, Lazy<PlanConfig> config,
            Lazy<Theme> theme,
            Lazy<DBSystem> dbSystem,
            Lazy<ServerInfo> serverInfo,
            Lazy<JSONStorage> jsonStorage,
            Lazy<Formatters> formatters,
            Lazy<Locale> locale,
            Lazy<ComponentSvc> componentService,
            Lazy<Addresses> addresses
    ) {
        this.versionChecker = versionChecker;
        this.files = files;
        this.publicHtmlFiles = publicHtmlFiles;
        this.config = config;
        this.theme = theme;
        this.dbSystem = dbSystem;
        this.serverInfo = serverInfo;
        this.jsonStorage = jsonStorage;
        this.formatters = formatters;
        this.locale = locale;
        this.componentService = componentService;
        this.addresses = addresses;
    }

    public Page playersPage() throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new PlayersPage(getResourceAsString("players.html"), versionChecker.get(),
                config.get(), theme.get(), serverInfo.get());
    }

    public Page reactPage() throws IOException {
        try {
            String fileName = "index.html";
            WebResource resource = ResourceService.getInstance().getResource(
                    "Plan", fileName, () -> getPublicHtmlOrJarResource(fileName)
            );
            return new ReactPage(getBasePath(), resource);
        } catch (UncheckedIOException readFail) {
            throw readFail.getCause();
        }
    }

    private String getBasePath() {
        String address = addresses.get().getMainAddress()
                .orElseGet(addresses.get()::getFallbackLocalhostAddress);
        return addresses.get().getBasePath(address);
    }

    /**
     * Create a server page.
     *
     * @param serverUUID UUID of the server
     * @return {@link Page} that matches the server page.
     * @throws NotFoundException If the server can not be found in the database.
     * @throws IOException       If the template files can not be read.
     */
    public Page serverPage(ServerUUID serverUUID) throws IOException {
        Server server = dbSystem.get().getDatabase().query(ServerQueries.fetchServerMatchingIdentifier(serverUUID))
                .orElseThrow(() -> new NotFoundException("Server not found in the database"));

        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new ServerPage(
                getResourceAsString("server.html"),
                server,
                config.get(),
                theme.get(),
                versionChecker.get(),
                dbSystem.get(),
                serverInfo.get(),
                jsonStorage.get(),
                formatters.get(),
                locale.get(),
                componentService.get()
        );
    }

    public Page playerPage(PlayerContainer player) throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new PlayerPage(
                getResourceAsString("player.html"), player,
                versionChecker.get(),
                config.get(),
                this,
                theme.get(),
                formatters.get(),
                serverInfo.get(),
                locale.get()
        );
    }

    public PlayerPluginTab inspectPluginTabs(UUID playerUUID) {
        Database database = dbSystem.get().getDatabase();

        Map<ServerUUID, List<ExtensionData>> extensionPlayerData = database.query(new ExtensionPlayerDataQuery(playerUUID));

        if (extensionPlayerData.isEmpty()) {
            return new PlayerPluginTab("", Collections.emptyList(), formatters.get(), componentService.get());
        }

        List<PlayerPluginTab> playerPluginTabs = new ArrayList<>();
        for (Map.Entry<ServerUUID, Server> entry : database.query(ServerQueries.fetchPlanServerInformation()).entrySet()) {
            ServerUUID serverUUID = entry.getKey();
            String serverName = entry.getValue().getIdentifiableName();

            List<ExtensionData> ofServer = extensionPlayerData.get(serverUUID);
            if (ofServer == null) {
                continue;
            }

            playerPluginTabs.add(new PlayerPluginTab(serverName, ofServer, formatters.get(), componentService.get()));
        }

        StringBuilder navs = new StringBuilder();
        StringBuilder tabs = new StringBuilder();

        playerPluginTabs.stream().sorted().forEach(tab -> {
            navs.append(tab.getNav());
            tabs.append(tab.getTab());
        });

        return new PlayerPluginTab(navs.toString(), tabs.toString(), componentService.get());
    }

    public Page networkPage() throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new NetworkPage(getResourceAsString("network.html"),
                dbSystem.get(),
                versionChecker.get(),
                config.get(),
                theme.get(),
                serverInfo.get(),
                jsonStorage.get(),
                formatters.get(),
                locale.get(),
                componentService.get()
        );
    }

    public Page internalErrorPage(String message, @Untrusted Throwable error) {
        try {
            return new InternalErrorPage(
                    getResourceAsString("error.html"), message, error,
                    versionChecker.get());
        } catch (IOException noParse) {
            return () -> "Error occurred: " + error.toString() +
                    ", additional error occurred when attempting to render error page to user: " +
                    noParse;
        }
    }

    public Page errorPage(String title, String error) throws IOException {
        return new ErrorMessagePage(
                getResourceAsString("error.html"), title, error,
                versionChecker.get(), theme.get());
    }

    public Page errorPage(Icon icon, String title, String error) throws IOException {
        return new ErrorMessagePage(
                getResourceAsString("error.html"), icon, title, error, theme.get(), versionChecker.get());
    }

    public String getResourceAsString(String name) throws IOException {
        return getResource(name).asString();
    }

    public WebResource getResource(String resourceName) throws IOException {
        try {
            return ResourceService.getInstance().getResource("Plan", resourceName,
                    () -> files.get().getResourceFromJar("web/" + resourceName).asWebResource()
            );
        } catch (UncheckedIOException readFail) {
            throw readFail.getCause();
        }
    }

    public WebResource getPublicHtmlOrJarResource(String resourceName) {
        return publicHtmlFiles.get().findPublicHtmlResource(resourceName)
                .orElseGet(() -> files.get().getResourceFromJar("web/" + resourceName))
                .asWebResource();
    }

    public Page loginPage() throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new LoginPage(getResource("login.html"), serverInfo.get(), locale.get(), theme.get(), versionChecker.get());
    }

    public Page registerPage() throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }

        return new LoginPage(getResource("register.html"), serverInfo.get(), locale.get(), theme.get(), versionChecker.get());
    }

    public Page queryPage() throws IOException {
        if (config.get().isFalse(PluginSettings.LEGACY_FRONTEND)) {
            return reactPage();
        }
        return new QueryPage(
                getResourceAsString("query.html"),
                locale.get(), theme.get(), versionChecker.get()
        );
    }

    public Page errorsPage() throws IOException {
        return reactPage();
    }
}