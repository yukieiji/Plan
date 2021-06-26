/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.djrapitops.plan.gathering.timed;

import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.delivery.domain.DateObj;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.settings.config.paths.TimeSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.transactions.events.PingStoreTransaction;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.playeranalytics.plugin.scheduling.RunnableFactory;
import net.playeranalytics.plugin.scheduling.TimeAmount;
import net.playeranalytics.plugin.server.Listeners;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Task that handles player ping calculation on Bungee based servers.
 *
 * @author BrainStone
 */
@Singleton
public class BungeePingCounter extends TaskSystem.Task implements Listener {

    private final Map<UUID, List<DateObj<Integer>>> playerHistory;

    private final Listeners listeners;
    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final ServerInfo serverInfo;
    private final RunnableFactory runnableFactory;

    @Inject
    public BungeePingCounter(
            Listeners listeners,
            PlanConfig config,
            DBSystem dbSystem,
            ServerInfo serverInfo,
            RunnableFactory runnableFactory
    ) {
        this.listeners = listeners;
        this.config = config;
        this.dbSystem = dbSystem;
        this.serverInfo = serverInfo;
        this.runnableFactory = runnableFactory;
        playerHistory = new HashMap<>();
    }

    @Override
    public void run() {
        long time = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<DateObj<Integer>>>> iterator = playerHistory.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, List<DateObj<Integer>>> entry = iterator.next();
            UUID uuid = entry.getKey();
            List<DateObj<Integer>> history = entry.getValue();
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
            if (player != null) {
                int ping = getPing(player);
                if (ping < -1 || ping > TimeUnit.SECONDS.toMillis(8L)) {
                    // Don't accept bad values
                    continue;
                }
                history.add(new DateObj<>(time, ping));
                if (history.size() >= 30) {
                    dbSystem.getDatabase().executeTransaction(
                            new PingStoreTransaction(uuid, serverInfo.getServerUUID(), new ArrayList<>(history))
                    );
                    history.clear();
                }
            } else {
                iterator.remove();
            }
        }
    }

    @Override
    public void register(RunnableFactory runnableFactory) {
        Long startDelay = config.get(TimeSettings.PING_SERVER_ENABLE_DELAY);
        if (startDelay < TimeUnit.HOURS.toMillis(1L) && config.isTrue(DataGatheringSettings.PING)) {
            listeners.registerListener(this);

            long delay = TimeAmount.toTicks(startDelay, TimeUnit.MILLISECONDS);
            long period = 40L;
            runnableFactory.create(this).runTaskTimerAsynchronously(delay, period);
        }
    }

    public void addPlayer(ProxiedPlayer player) {
        playerHistory.put(player.getUniqueId(), new ArrayList<>());
    }

    public void removePlayer(ProxiedPlayer player) {
        playerHistory.remove(player.getUniqueId());
    }

    private int getPing(ProxiedPlayer player) {
        return player.getPing();
    }

    @EventHandler
    public void onPlayerJoin(ServerConnectedEvent joinEvent) {
        ProxiedPlayer player = joinEvent.getPlayer();
        Long pingDelay = config.get(TimeSettings.PING_PLAYER_LOGIN_DELAY);
        if (pingDelay >= TimeUnit.HOURS.toMillis(2L)) {
            return;
        }
        runnableFactory.create(() -> {
            if (player.isConnected()) {
                addPlayer(player);
            }
        }).runTaskLater(TimeAmount.toTicks(pingDelay, TimeUnit.MILLISECONDS));
    }

    @EventHandler
    public void onPlayerQuit(ServerDisconnectEvent quitEvent) {
        removePlayer(quitEvent.getPlayer());
    }

    public void clear() {
        playerHistory.clear();
    }
}
