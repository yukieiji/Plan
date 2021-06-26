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
package utilities.dagger;

import com.djrapitops.plan.settings.config.ExtensionSettings;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.LocaleSystem;
import com.djrapitops.plan.storage.file.JarResource;
import com.djrapitops.plan.utilities.logging.ErrorLogger;
import dagger.Module;
import dagger.Provides;
import utilities.TestErrorLogger;
import utilities.TestResources;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.function.Predicate;

@Module
public class TestSystemObjectProvidingModule {

    @Provides
    @Singleton
    Locale provideLocale(LocaleSystem localeSystem) {
        return localeSystem.getLocale();
    }

    @Provides
    @Singleton
    ExtensionSettings providePluginsConfigSection(PlanConfig config) {
        return config.getExtensionSettings();
    }

    @Provides
    @Singleton
    @Named("isExtensionEnabled")
    Predicate<String> provideExtensionEnabledConfigCheck(PlanConfig config) {
        return config.getExtensionSettings()::isEnabled;
    }

    @Provides
    @Singleton
    JarResource.StreamFunction provideJarStreamFunction(@Named("tempDir") Path tempDir) {
        return resource -> {
            File copyTo = tempDir.resolve(resource).toFile();
            TestResources.copyResourceIntoFile(copyTo, "/" + resource);
            return new FileInputStream(copyTo);
        };
    }

    @Provides
    @Singleton
    @Named("dataFolder")
    File provideDataFolder(@Named("tempDir") Path tempDir) {
        return tempDir.toFile();
    }

    @Provides
    @Singleton
    ErrorLogger provideErrorLogger() {
        return new TestErrorLogger();
    }
}
