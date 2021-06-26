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
package com.djrapitops.plan.modules.bungee;

import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.delivery.webserver.cache.JSONFileStorage;
import com.djrapitops.plan.extension.ExtensionServerDataUpdater;
import com.djrapitops.plan.gathering.timed.BungeePingCounter;
import com.djrapitops.plan.gathering.timed.ProxyTPSCounter;
import com.djrapitops.plan.gathering.timed.SystemUsageBuffer;
import com.djrapitops.plan.settings.upkeep.NetworkConfigStoreTask;
import com.djrapitops.plan.storage.upkeep.DBCleanTask;
import com.djrapitops.plan.storage.upkeep.LogsFolderCleanTask;
import com.djrapitops.plan.storage.upkeep.OldDependencyCacheDeletionTask;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

@Module
public interface BungeeTaskModule {

    @Binds
    @IntoSet
    TaskSystem.Task bindTPSCounter(ProxyTPSCounter counter);

    @Binds
    @IntoSet
    TaskSystem.Task bindPingCounter(BungeePingCounter counter);

    @Binds
    @IntoSet
    TaskSystem.Task bindNetworkConfigStoreTask(NetworkConfigStoreTask configStoreTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindExtensionServerDataUpdater(ExtensionServerDataUpdater extensionServerDataUpdater);

    @Binds
    @IntoSet
    TaskSystem.Task bindLogCleanTask(LogsFolderCleanTask logsFolderCleanTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindDBCleanTask(DBCleanTask cleanTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindRamAndCpuTask(SystemUsageBuffer.RamAndCpuTask ramAndCpuTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindDiskTask(SystemUsageBuffer.DiskTask diskTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindJSONFileStorageCleanTask(JSONFileStorage.CleanTask cleanTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindOldDependencyCacheDeletion(OldDependencyCacheDeletionTask deletionTask);
}
