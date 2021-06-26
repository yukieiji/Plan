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

import com.djrapitops.plan.commands.PlanCommand;
import com.djrapitops.plan.gathering.ServerShutdownSave;
import com.djrapitops.plan.modules.*;
import com.djrapitops.plan.modules.sponge.SpongeServerPropertiesModule;
import com.djrapitops.plan.modules.sponge.SpongeSuperClassBindingModule;
import com.djrapitops.plan.modules.sponge.SpongeTaskModule;
import dagger.BindsInstance;
import dagger.Component;
import net.playeranalytics.plugin.PlatformAbstractionLayer;
import org.spongepowered.api.Game;

import javax.inject.Singleton;

/**
 * Dagger Component that constructs the plugin systems running on Sponge.
 *
 * @author AuroraLS3
 */
@Singleton
@Component(modules = {
        SystemObjectProvidingModule.class,
        PlatformAbstractionLayerModule.class,
        FiltersModule.class,
        PlaceholderModule.class,

        ServerCommandModule.class,
        SpongeSuperClassBindingModule.class,
        SpongeServerPropertiesModule.class,
        SpongeTaskModule.class
})
public interface PlanSpongeComponent {

    PlanCommand planCommand();

    PlanSystem system();

    ServerShutdownSave serverShutdownSave();

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder plan(PlanPlugin plan);

        @BindsInstance
        Builder abstractionLayer(PlatformAbstractionLayer abstractionLayer);

        @BindsInstance
        Builder game(Game game);

        PlanSpongeComponent build();
    }
}