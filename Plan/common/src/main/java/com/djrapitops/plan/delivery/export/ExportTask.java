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
package com.djrapitops.plan.delivery.export;

import com.djrapitops.plan.exceptions.ExportException;
import com.djrapitops.plan.exceptions.database.DBOpException;
import com.djrapitops.plan.utilities.java.ThrowingConsumer;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;
import net.playeranalytics.plugin.scheduling.PluginRunnable;

public class ExportTask extends PluginRunnable {

    private final Exporter exporter;
    private final ThrowingConsumer<Exporter, ExportException> exportAction;
    private final ErrorLogger errorLogger;

    public ExportTask(
            Exporter exporter,
            ThrowingConsumer<Exporter, ExportException> exportAction,
            ErrorLogger errorLogger
    ) {
        this.exporter = exporter;
        this.exportAction = exportAction;
        this.errorLogger = errorLogger;
    }

    @Override
    public void run() {
        try {
            exportAction.accept(exporter);
        } catch (ExportException e) {
            errorLogger.warn(e, ErrorContext.builder().related(getClass()).build());
        } catch (DBOpException dbException) {
            handleDBException(dbException);
        } catch (Exception | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError e) {
            errorLogger.error(e, ErrorContext.builder()
                    .whatToDo("Export Task Disabled due to error - reload Plan to re-enable.")
                    .related(getClass()).build());
            cancel();
        }
    }

    private void handleDBException(DBOpException dbException) {
        if (dbException.getMessage().contains("closed")) {
            errorLogger.error(dbException, ErrorContext.builder()
                    .whatToDo("Export Task Disabled due to error - database is closing, so this error can be ignored.).")
                    .related(getClass()).build());
        } else {
            errorLogger.error(dbException, ErrorContext.builder()
                    .whatToDo("Export Task Disabled due to error - reload Plan to re-enable.")
                    .related(getClass()).build());
        }
        cancel();
    }
}
