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
package com.djrapitops.plan.gathering.timed;

import com.djrapitops.plan.TaskSystem;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;
import net.playeranalytics.plugin.scheduling.RunnableFactory;
import net.playeranalytics.plugin.scheduling.TimeAmount;
import net.playeranalytics.plugin.server.PluginLogger;

import java.util.concurrent.TimeUnit;

/**
 * Class responsible for calculating TPS every second.
 *
 * @author AuroraLS3
 */
public abstract class TPSCounter extends TaskSystem.Task {

    protected final PluginLogger logger;
    protected final ErrorLogger errorLogger;

    protected TPSCounter(
            PluginLogger logger,
            ErrorLogger errorLogger
    ) {
        this.logger = logger;
        this.errorLogger = errorLogger;
    }

    @Override
    public void run() {
        try {
            pulse();
        } catch (Exception | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError e) {
            logger.error("TPS Count Task Disabled due to error, reload Plan to re-enable.");
            errorLogger.error(e, ErrorContext.builder().whatToDo("See if a restart fixes this or Report this").build());
            cancel();
        }
    }

    public void register(RunnableFactory runnableFactory) {
        long delay = TimeAmount.toTicks(1L, TimeUnit.MINUTES);
        long period = TimeAmount.toTicks(1L, TimeUnit.SECONDS);
        runnableFactory.create(this).runTaskTimer(delay, period);
    }

    public abstract void pulse();

}
