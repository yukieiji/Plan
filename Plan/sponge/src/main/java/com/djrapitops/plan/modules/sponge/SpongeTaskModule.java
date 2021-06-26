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
package com.djrapitops.plan.modules.sponge;

import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.delivery.webserver.cache.JSONFileStorage;
import com.djrapitops.plan.extension.ExtensionServerDataUpdater;
import com.djrapitops.plan.gathering.ShutdownDataPreservation;
import com.djrapitops.plan.gathering.ShutdownHook;
import com.djrapitops.plan.gathering.timed.ServerTPSCounter;
import com.djrapitops.plan.gathering.timed.SpongePingCounter;
import com.djrapitops.plan.gathering.timed.SystemUsageBuffer;
import com.djrapitops.plan.settings.upkeep.ConfigStoreTask;
import com.djrapitops.plan.storage.upkeep.DBCleanTask;
import com.djrapitops.plan.storage.upkeep.LogsFolderCleanTask;
import com.djrapitops.plan.storage.upkeep.OldDependencyCacheDeletionTask;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import org.spongepowered.api.world.World;

@Module
public interface SpongeTaskModule {

    @Binds
    @IntoSet
    TaskSystem.Task bindTPSCounter(ServerTPSCounter<World> tpsCounter);

    @Binds
    @IntoSet
    TaskSystem.Task bindPingCounter(SpongePingCounter pingCounter);

    @Binds
    @IntoSet
    TaskSystem.Task bindExtensionServerDataUpdater(ExtensionServerDataUpdater extensionServerDataUpdater);

    @Binds
    @IntoSet
    TaskSystem.Task bindLogCleanTask(LogsFolderCleanTask logsFolderCleanTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindConfigStoreTask(ConfigStoreTask configStoreTask);

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
    TaskSystem.Task bindShutdownHookRegistration(ShutdownHook.Registrar registrar);

    @Binds
    @IntoSet
    TaskSystem.Task bindJSONFileStorageCleanTask(JSONFileStorage.CleanTask cleanTask);

    @Binds
    @IntoSet
    TaskSystem.Task bindShutdownDataPreservation(ShutdownDataPreservation dataPreservation);

    @Binds
    @IntoSet
    TaskSystem.Task bindOldDependencyCacheDeletion(OldDependencyCacheDeletionTask deletionTask);
}
