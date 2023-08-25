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

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.delivery.domain.DateObj;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.settings.config.paths.TimeSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.transactions.events.PingStoreTransaction;
import net.playeranalytics.plugin.scheduling.RunnableFactory;
import net.playeranalytics.plugin.scheduling.TimeAmount;
import net.playeranalytics.plugin.server.Listeners;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Task that handles player ping calculation on Nukkit based servers.
 * <p>
 * Modified PingManager from LagMonitor plugin.
 * https://github.com/games647/LagMonitor/blob/master/src/main/java/com/github/games647/lagmonitor/task/PingManager.java
 *
 * @author games647
 */
@Singleton
public class NukkitPingCounter extends TaskSystem.Task implements Listener {

    private final Map<UUID, Long> startRecording;
    private final Map<UUID, List<DateObj<Integer>>> playerHistory;

    private final Listeners listeners;
    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final ServerInfo serverInfo;

    @Inject
    public NukkitPingCounter(
            Listeners listeners,
            PlanConfig config,
            DBSystem dbSystem,
            ServerInfo serverInfo
    ) {
        this.listeners = listeners;
        this.config = config;
        this.dbSystem = dbSystem;
        this.serverInfo = serverInfo;
        startRecording = new ConcurrentHashMap<>();
        playerHistory = new HashMap<>();
    }

    @Override
    public void run() {
        long time = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> starts = startRecording.entrySet().iterator();
        while (starts.hasNext()) {
            Map.Entry<UUID, Long> start = starts.next();
            if (time >= start.getValue()) {
                addPlayer(start.getKey());
                starts.remove();
            }
        }

        Iterator<Map.Entry<UUID, List<DateObj<Integer>>>> iterator = playerHistory.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, List<DateObj<Integer>>> entry = iterator.next();
            UUID uuid = entry.getKey();
            List<DateObj<Integer>> history = entry.getValue();
            Optional<Player> player = Server.getInstance().getPlayer(uuid);
            if (player.isPresent()) {
                int ping = player.get().getPing();
                if (ping <= -1 || ping > TimeUnit.SECONDS.toMillis(8L)) {
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
        Long enableDelay = config.get(TimeSettings.PING_SERVER_ENABLE_DELAY);
        if (enableDelay < TimeUnit.HOURS.toMillis(1L) && config.isTrue(DataGatheringSettings.PING)) {
            listeners.registerListener(this);
            long delay = TimeAmount.toTicks(enableDelay, TimeUnit.MILLISECONDS);
            long period = 40L;
            runnableFactory.create(this).runTaskTimer(delay, period);
        }
    }

    public void addPlayer(UUID uuid) {
        playerHistory.put(uuid, new ArrayList<>());
    }

    public void removePlayer(Player player) {
        playerHistory.remove(player.getUniqueId());
        startRecording.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent joinEvent) {
        Player player = joinEvent.getPlayer();
        Long pingDelayMs = config.get(TimeSettings.PING_PLAYER_LOGIN_DELAY);
        if (pingDelayMs >= TimeUnit.HOURS.toMillis(2L)) {
            return;
        }
        startRecording.put(player.getUniqueId(), System.currentTimeMillis() + pingDelayMs);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent quitEvent) {
        removePlayer(quitEvent.getPlayer());
    }

    public void clear() {
        playerHistory.clear();
    }
}
