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
package com.djrapitops.plan;

import com.djrapitops.plan.addons.placeholderapi.BukkitPlaceholderRegistrar;
import com.djrapitops.plan.commands.PlanCommand;
import com.djrapitops.plan.gathering.ServerShutdownSave;
import com.djrapitops.plan.modules.*;
import com.djrapitops.plan.modules.bukkit.BukkitPlanModule;
import com.djrapitops.plan.modules.bukkit.BukkitServerPropertiesModule;
import com.djrapitops.plan.modules.bukkit.BukkitSuperClassBindingModule;
import com.djrapitops.plan.modules.bukkit.BukkitTaskModule;
import dagger.BindsInstance;
import dagger.Component;
import net.playeranalytics.plugin.PlatformAbstractionLayer;
import org.bukkit.Server;

import javax.inject.Singleton;

/**
 * Dagger Component that constructs the plugin systems running on Bukkit.
 *
 * @author AuroraLS3
 */
@Singleton
@Component(modules = {
        BukkitPlanModule.class,
        SystemObjectProvidingModule.class,
        PlatformAbstractionLayerModule.class,
        FiltersModule.class,
        PlaceholderModule.class,

        ServerCommandModule.class,
        BukkitServerPropertiesModule.class,
        BukkitSuperClassBindingModule.class,
        BukkitTaskModule.class
})
public interface PlanBukkitComponent {

    PlanCommand planCommand();

    PlanSystem system();

    BukkitPlaceholderRegistrar placeholders();

    ServerShutdownSave serverShutdownSave();

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder plan(PlanPlugin plan);

        @BindsInstance
        Builder abstractionLayer(PlatformAbstractionLayer abstractionLayer);

        @BindsInstance
        Builder server(Server server);

        PlanBukkitComponent build();
    }
}