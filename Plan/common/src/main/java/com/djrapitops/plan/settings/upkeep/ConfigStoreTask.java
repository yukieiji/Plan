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
package com.djrapitops.plan.settings.upkeep;

import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.TimeSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.transactions.StoreConfigTransaction;
import com.djrapitops.plan.storage.file.PlanFiles;
import net.playeranalytics.plugin.scheduling.RunnableFactory;
import net.playeranalytics.plugin.scheduling.TimeAmount;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

/**
 * Task that stores a server config in the database on boot.
 *
 * @author AuroraLS3
 */
@Singleton
public class ConfigStoreTask extends TaskSystem.Task {

    private final PlanFiles files;
    private final PlanConfig config;
    private final ServerInfo serverInfo;
    private final DBSystem dbSystem;

    @Inject
    public ConfigStoreTask(
            PlanFiles files,
            PlanConfig config,
            ServerInfo serverInfo,
            DBSystem dbSystem
    ) {
        this.files = files;
        this.config = config;
        this.serverInfo = serverInfo;
        this.dbSystem = dbSystem;
    }

    @Override
    public void run() {
        long lastModified = files.getConfigFile().lastModified();
        dbSystem.getDatabase().executeTransaction(new StoreConfigTransaction(serverInfo.getServerUUID(), config, lastModified));
        cancel();
    }

    @Override
    public void register(RunnableFactory runnableFactory) {
        long delay = TimeAmount.toTicks(config.get(TimeSettings.CONFIG_UPDATE_INTERVAL), TimeUnit.MILLISECONDS) + 40;
        runnableFactory.create(this).runTaskLaterAsynchronously(delay);
    }
}