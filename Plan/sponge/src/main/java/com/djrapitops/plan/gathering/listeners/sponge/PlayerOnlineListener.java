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
package com.djrapitops.plan.gathering.listeners.sponge;

import com.djrapitops.plan.delivery.domain.Nickname;
import com.djrapitops.plan.delivery.domain.keys.SessionKeys;
import com.djrapitops.plan.delivery.export.Exporter;
import com.djrapitops.plan.delivery.webserver.cache.DataID;
import com.djrapitops.plan.delivery.webserver.cache.JSONCache;
import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.ExtensionServiceImplementation;
import com.djrapitops.plan.gathering.cache.GeolocationCache;
import com.djrapitops.plan.gathering.cache.NicknameCache;
import com.djrapitops.plan.gathering.cache.SessionCache;
import com.djrapitops.plan.gathering.domain.Session;
import com.djrapitops.plan.gathering.listeners.Status;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.settings.config.paths.ExportSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.transactions.events.*;
import com.djrapitops.plugin.logging.L;
import com.djrapitops.plugin.logging.error.ErrorHandler;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.entity.living.humanoid.player.KickPlayerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.ban.BanService;

import javax.inject.Inject;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * Listener for Player Join/Leave on Sponge.
 *
 * @author Rsl1122
 */
public class PlayerOnlineListener {

    private final PlanConfig config;
    private final Processing processing;
    private final ServerInfo serverInfo;
    private final DBSystem dbSystem;
    private final ExtensionServiceImplementation extensionService;
    private final Exporter exporter;
    private final GeolocationCache geolocationCache;
    private final NicknameCache nicknameCache;
    private final SessionCache sessionCache;
    private final Status status;
    private final ErrorHandler errorHandler;

    @Inject
    public PlayerOnlineListener(
            PlanConfig config,
            Processing processing,
            ServerInfo serverInfo,
            DBSystem dbSystem,
            ExtensionServiceImplementation extensionService,
            Exporter exporter, GeolocationCache geolocationCache,
            NicknameCache nicknameCache,
            SessionCache sessionCache,
            Status status,
            ErrorHandler errorHandler
    ) {
        this.config = config;
        this.processing = processing;
        this.serverInfo = serverInfo;
        this.dbSystem = dbSystem;
        this.extensionService = extensionService;
        this.exporter = exporter;
        this.geolocationCache = geolocationCache;
        this.nicknameCache = nicknameCache;
        this.sessionCache = sessionCache;
        this.status = status;
        this.errorHandler = errorHandler;
    }

    @Listener(order = Order.POST)
    public void onLogin(ClientConnectionEvent.Login event) {
        try {
            actOnLoginEvent(event);
        } catch (Exception e) {
            errorHandler.log(L.ERROR, this.getClass(), e);
        }
    }

    private void actOnLoginEvent(ClientConnectionEvent.Login event) {
        GameProfile profile = event.getProfile();
        UUID playerUUID = profile.getUniqueId();
        boolean banned = isBanned(profile);
        dbSystem.getDatabase().executeTransaction(new BanStatusTransaction(playerUUID, () -> banned));
    }

    @Listener(order = Order.POST)
    public void onKick(KickPlayerEvent event) {
        try {
            UUID playerUUID = event.getTargetEntity().getUniqueId();
            if (!status.areKicksCounted() || SpongeAFKListener.AFK_TRACKER.isAfk(playerUUID)) {
                return;
            }
            dbSystem.getDatabase().executeTransaction(new KickStoreTransaction(playerUUID));
        } catch (Exception e) {
            errorHandler.log(L.ERROR, this.getClass(), e);
        }
    }

    private boolean isBanned(GameProfile profile) {
        Optional<ProviderRegistration<BanService>> banService = Sponge.getServiceManager().getRegistration(BanService.class);
        boolean banned = false;
        if (banService.isPresent()) {
            banned = banService.get().getProvider().isBanned(profile);
        }
        return banned;
    }

    @Listener(order = Order.POST)
    public void onJoin(ClientConnectionEvent.Join event) {
        try {
            actOnJoinEvent(event);
        } catch (Exception e) {
            errorHandler.log(L.ERROR, this.getClass(), e);
        }
    }

    private void actOnJoinEvent(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();

        UUID playerUUID = player.getUniqueId();
        UUID serverUUID = serverInfo.getServerUUID();
        long time = System.currentTimeMillis();
        JSONCache.invalidate(DataID.SERVER_OVERVIEW, serverUUID);
        JSONCache.invalidate(DataID.GRAPH_PERFORMANCE, serverUUID);

        SpongeAFKListener.AFK_TRACKER.performedAction(playerUUID, time);

        String world = player.getWorld().getName();
        Optional<GameMode> gameMode = player.getGameModeData().get(Keys.GAME_MODE);
        String gm = gameMode.map(mode -> mode.getName().toUpperCase()).orElse("ADVENTURE");

        Database database = dbSystem.getDatabase();
        database.executeTransaction(new WorldNameStoreTransaction(serverUUID, world));

        InetAddress address = player.getConnection().getAddress().getAddress();

        String playerName = player.getName();
        String displayName = player.getDisplayNameData().displayName().get().toPlain();

        boolean gatheringGeolocations = config.isTrue(DataGatheringSettings.GEOLOCATIONS);
        if (gatheringGeolocations) {
            database.executeTransaction(
                    new GeoInfoStoreTransaction(playerUUID, address, time, geolocationCache::getCountry)
            );
        }

        database.executeTransaction(new PlayerServerRegisterTransaction(playerUUID, () -> time, playerName, serverUUID));
        Session session = new Session(playerUUID, serverUUID, time, world, gm);
        session.putRawData(SessionKeys.NAME, playerName);
        session.putRawData(SessionKeys.SERVER_NAME, serverInfo.getServer().getIdentifiableName());
        sessionCache.cacheSession(playerUUID, session)
                .ifPresent(previousSession -> database.executeTransaction(new SessionEndTransaction(previousSession)));

        database.executeTransaction(new NicknameStoreTransaction(
                playerUUID, new Nickname(displayName, time, serverUUID),
                (uuid, name) -> name.equals(nicknameCache.getDisplayName(uuid))
        ));

        processing.submitNonCritical(() -> extensionService.updatePlayerValues(playerUUID, playerName, CallEvents.PLAYER_JOIN));
        if (config.get(ExportSettings.EXPORT_ON_ONLINE_STATUS_CHANGE)) {
            processing.submitNonCritical(() -> exporter.exportPlayerPage(playerUUID, playerName));
        }
    }

    @Listener(order = Order.DEFAULT)
    public void beforeQuit(ClientConnectionEvent.Disconnect event) {
        Player player = event.getTargetEntity();
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();
        processing.submitNonCritical(() -> extensionService.updatePlayerValues(playerUUID, playerName, CallEvents.PLAYER_LEAVE));
    }

    @Listener(order = Order.POST)
    public void onQuit(ClientConnectionEvent.Disconnect event) {
        try {
            actOnQuitEvent(event);
        } catch (Exception e) {
            errorHandler.log(L.ERROR, this.getClass(), e);
        }
    }

    private void actOnQuitEvent(ClientConnectionEvent.Disconnect event) {
        long time = System.currentTimeMillis();
        Player player = event.getTargetEntity();
        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();
        UUID serverUUID = serverInfo.getServerUUID();
        JSONCache.invalidate(DataID.SERVER_OVERVIEW, serverUUID);
        JSONCache.invalidate(DataID.GRAPH_PERFORMANCE, serverUUID);

        SpongeAFKListener.AFK_TRACKER.loggedOut(playerUUID, time);

        nicknameCache.removeDisplayName(playerUUID);

        boolean banned = isBanned(player.getProfile());
        dbSystem.getDatabase().executeTransaction(new BanStatusTransaction(playerUUID, () -> banned));

        sessionCache.endSession(playerUUID, time)
                .ifPresent(endedSession -> dbSystem.getDatabase().executeTransaction(new SessionEndTransaction(endedSession)));

        if (config.get(ExportSettings.EXPORT_ON_ONLINE_STATUS_CHANGE)) {
            processing.submitNonCritical(() -> exporter.exportPlayerPage(playerUUID, playerName));
        }
    }
}