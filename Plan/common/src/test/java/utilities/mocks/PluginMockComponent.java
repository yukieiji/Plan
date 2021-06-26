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
package utilities.mocks;

import com.djrapitops.plan.PlanPlugin;
import com.djrapitops.plan.PlanSystem;
import com.djrapitops.plan.utilities.logging.PluginErrorLogger;
import net.playeranalytics.plugin.PlatformAbstractionLayer;
import utilities.dagger.DaggerPlanPluginComponent;
import utilities.dagger.PlanPluginComponent;

import java.nio.file.Path;

/**
 * Test utility for creating a dagger PlanComponent using a mocked Plan.
 *
 * @author AuroraLS3
 */
public class PluginMockComponent {

    private final Path tempDir;

    private PlanPlugin planMock;
    private PlanPluginComponent component;
    private TestPlatformAbstractionLayer abstractionLayer;

    public PluginMockComponent(Path tempDir) {
        this.tempDir = tempDir;
    }

    public PlanPlugin getPlanMock() throws Exception {
        if (planMock == null) {
            planMock = PlanPluginMocker.setUp()
                    .withDataFolder(tempDir.toFile())
                    .withLogging().getPlanMock();
        }
        return planMock;
    }

    public PlanSystem getPlanSystem() throws Exception {
        initComponent();
        return component.system();
    }

    private void initComponent() throws Exception {
        if (component == null) {
            PlanPlugin planMock = getPlanMock();
            abstractionLayer = new TestPlatformAbstractionLayer(planMock);
            component = DaggerPlanPluginComponent.builder()
                    .bindTemporaryDirectory(tempDir)
                    .plan(planMock)
                    .abstractionLayer(abstractionLayer)
                    .build();
        }
    }

    public PluginErrorLogger getPluginErrorLogger() throws Exception {
        initComponent();
        return component.pluginErrorLogger();
    }

    public PlanPluginComponent getComponent() throws Exception {
        initComponent();
        return component;
    }

    public PlatformAbstractionLayer getAbstractionLayer() throws Exception {
        initComponent();
        return abstractionLayer;
    }
}