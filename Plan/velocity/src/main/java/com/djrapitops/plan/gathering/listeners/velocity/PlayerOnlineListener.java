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
package com.djrapitops.plan.gathering.listeners.velocity;

import com.djrapitops.plan.delivery.domain.keys.SessionKeys;
import com.djrapitops.plan.delivery.export.Exporter;
import com.djrapitops.plan.delivery.webserver.cache.DataID;
import com.djrapitops.plan.delivery.webserver.cache.JSONCache;
import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.ExtensionServiceImplementation;
import com.djrapitops.plan.gathering.cache.GeolocationCache;
import com.djrapitops.plan.gathering.cache.SessionCache;
import com.djrapitops.plan.gathering.domain.Session;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.settings.config.paths.ExportSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.transactions.events.GeoInfoStoreTransaction;
import com.djrapitops.plan.storage.database.transactions.events.PlayerRegisterTransaction;
import com.djrapitops.plugin.logging.L;
import com.djrapitops.plugin.logging.error.ErrorHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Player Join listener for Velocity.
 * <p>
 * Based on the bungee version.
 *
 * @author MicleBrick
 */
@Singleton
public class PlayerOnlineListener {

    private final PlanConfig config;
    private final Processing processing;
    private final DBSystem dbSystem;
    private final ExtensionServiceImplementation extensionService;
    private final Exporter exporter;
    private final GeolocationCache geolocationCache;
    private final SessionCache sessionCache;
    private final ServerInfo serverInfo;
    private final ErrorHandler errorHandler;

    @Inject
    public PlayerOnlineListener(
            PlanConfig config,
            Processing processing,
            DBSystem dbSystem,
            ExtensionServiceImplementation extensionService,
            Exporter exporter, GeolocationCache geolocationCache,
            SessionCache sessionCache,
            ServerInfo serverInfo,
            ErrorHandler errorHandler
    ) {
        this.config = config;
        this.processing = processing;
        this.dbSystem = dbSystem;
        this.extensionService = extensionService;
        this.exporter = exporter;
        this.geolocationCache = geolocationCache;
        this.sessionCache = sessionCache;
        this.serverInfo = serverInfo;
        this.errorHandler = errorHandler;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPostLogin(PostLoginEvent event) {
        try {
            actOnLogin(event);
        } catch (Exception e) {
            errorHandler.log(L.WARN, this.getClass(), e);
        }
    }

    public void actOnLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getUsername();
        InetAddress address = player.getRemoteAddress().getAddress();
        long time = System.currentTimeMillis();

        Session session = new Session(playerUUID, serverInfo.getServerUUID(), time, null, null);
        session.putRawData(SessionKeys.NAME, playerName);
        session.putRawData(SessionKeys.SERVER_NAME, "Proxy Server");
        sessionCache.cacheSession(playerUUID, session);

        Database database = dbSystem.getDatabase();

        boolean gatheringGeolocations = config.isTrue(DataGatheringSettings.GEOLOCATIONS);
        if (gatheringGeolocations) {
            database.executeTransaction(
                    new GeoInfoStoreTransaction(playerUUID, address, time, geolocationCache::getCountry)
            );
        }

        database.executeTransaction(new PlayerRegisterTransaction(playerUUID, () -> time, playerName));
        processing.submitNonCritical(() -> extensionService.updatePlayerValues(playerUUID, playerName, CallEvents.PLAYER_JOIN));
        if (config.get(ExportSettings.EXPORT_ON_ONLINE_STATUS_CHANGE)) {
            processing.submitNonCritical(() -> exporter.exportPlayerPage(playerUUID, playerName));
        }

        UUID serverUUID = serverInfo.getServerUUID();
        JSONCache.invalidateMatching(DataID.SERVER_OVERVIEW);
        JSONCache.invalidate(DataID.GRAPH_ONLINE, serverUUID);
        JSONCache.invalidate(DataID.SERVERS);
        JSONCache.invalidate(DataID.SESSIONS);
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void beforeLogout(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getUsername();
        processing.submitNonCritical(() -> extensionService.updatePlayerValues(playerUUID, playerName, CallEvents.PLAYER_LEAVE));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLogout(DisconnectEvent event) {
        try {
            actOnLogout(event);
        } catch (Exception e) {
            errorHandler.log(L.WARN, this.getClass(), e);
        }
    }

    public void actOnLogout(DisconnectEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        UUID playerUUID = player.getUniqueId();

        sessionCache.endSession(playerUUID, System.currentTimeMillis());
        if (config.get(ExportSettings.EXPORT_ON_ONLINE_STATUS_CHANGE)) {
            processing.submitNonCritical(() -> exporter.exportPlayerPage(playerUUID, playerName));
        }

        processing.submit(() -> {
            JSONCache.invalidateMatching(
                    DataID.SERVER_OVERVIEW,
                    DataID.SESSIONS,
                    DataID.GRAPH_WORLD_PIE,
                    DataID.GRAPH_PUNCHCARD,
                    DataID.KILLS,
                    DataID.ONLINE_OVERVIEW,
                    DataID.SESSIONS_OVERVIEW,
                    DataID.PVP_PVE,
                    DataID.GRAPH_UNIQUE_NEW,
                    DataID.GRAPH_CALENDAR
            );
            UUID serverUUID = serverInfo.getServerUUID();
            JSONCache.invalidate(DataID.GRAPH_ONLINE, serverUUID);
            JSONCache.invalidate(DataID.SERVERS);
        });
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerSwitch(ServerConnectedEvent event) {
        try {
            actOnServerSwitch(event);
        } catch (Exception e) {
            errorHandler.log(L.WARN, this.getClass(), e);
        }
    }

    public void actOnServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        UUID playerUUID = player.getUniqueId();
        long time = System.currentTimeMillis();

        // Replaces the current session in the cache.
        Session session = new Session(playerUUID, serverInfo.getServerUUID(), time, null, null);
        session.putRawData(SessionKeys.NAME, playerName);
        session.putRawData(SessionKeys.SERVER_NAME, "Proxy Server");
        sessionCache.cacheSession(playerUUID, session);

        if (config.get(ExportSettings.EXPORT_ON_ONLINE_STATUS_CHANGE)) {
            processing.submitNonCritical(() -> exporter.exportPlayerPage(playerUUID, playerName));
        }

        JSONCache.invalidate(DataID.SERVERS);
    }
}
