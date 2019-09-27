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
package com.djrapitops.plan.settings.config;

import com.djrapitops.plan.delivery.domain.keys.SessionKeys;
import com.djrapitops.plan.delivery.formatting.Formatter;
import com.djrapitops.plan.delivery.formatting.Formatters;
import com.djrapitops.plan.gathering.domain.GMTimes;
import com.djrapitops.plan.gathering.domain.Session;
import com.djrapitops.plan.gathering.domain.WorldTimes;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.config.paths.DisplaySettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.GenericLang;
import com.djrapitops.plan.settings.locale.lang.HtmlLang;
import com.djrapitops.plugin.logging.L;
import com.djrapitops.plugin.logging.error.ErrorHandler;
import com.djrapitops.plugin.utilities.Verify;
import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class responsible for managing config settings for World Aliases.
 *
 * @author Rsl1122
 */
@Singleton
public class WorldAliasSettings {

    private final Lazy<PlanConfig> config;
    private final Supplier<Formatter<Double>> percentageFormatter;
    private final Lazy<Locale> locale;
    private final Processing processing;
    private final ErrorHandler errorHandler;

    @Inject
    public WorldAliasSettings(
            Lazy<PlanConfig> config,
            Lazy<Locale> locale,
            Lazy<Formatters> formatters,
            Processing processing,
            ErrorHandler errorHandler
    ) {
        this.config = config;
        this.locale = locale;
        this.processing = processing;
        this.errorHandler = errorHandler;

        percentageFormatter = () -> formatters.get().percentage();
    }

    private ConfigNode getAliasSection() {
        return config.get().get(DisplaySettings.WORLD_ALIASES);
    }

    /**
     * Adds a new World to the config section.
     * <p>
     * If exists does not override old value.
     *
     * @param world World name
     */
    public void addWorld(String world) {
        Verify.isFalse(Verify.isEmpty(world), () -> new IllegalArgumentException("Attempted to save empty world alias"));

        ConfigNode aliasSect = getAliasSection();

        String previousValue = aliasSect.getString(world);
        if (Verify.isEmpty(previousValue)) {
            aliasSect.set(world, world);
            processing.submitNonCritical(() -> {
                try {
                    aliasSect.save();
                } catch (IOException e) {
                    errorHandler.log(L.WARN, WorldAliasSettings.class, e);
                }
            });
        }
    }

    public Map<String, Long> getPlaytimePerAlias(WorldTimes worldTimes) {
        if (worldTimes.getWorldTimes().isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Long> playtimePerWorld = worldTimes.getWorldTimes() // WorldTimes Map<String, GMTimes>
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getTotal() // GMTimes.getTotal
                ));

        ConfigNode aliases = getAliasSection();

        Map<String, Long> playtimePerAlias = new HashMap<>();
        for (Map.Entry<String, Long> entry : playtimePerWorld.entrySet()) {
            String worldName = entry.getKey();
            long playtime = entry.getValue();

            if (worldName != null && !aliases.contains(worldName)) {
                addWorld(worldName);
            }

            String alias = aliases.getString(worldName);

            playtimePerAlias.put(alias, playtimePerAlias.getOrDefault(alias, 0L) + playtime);
        }
        return playtimePerAlias;
    }

    public Map<String, GMTimes> getGMTimesPerAlias(WorldTimes worldTimes) {
        ConfigNode aliases = getAliasSection();

        Map<String, GMTimes> gmTimesPerAlias = new HashMap<>();

        String[] gms = GMTimes.getGMKeyArray();

        for (Map.Entry<String, GMTimes> entry : worldTimes.getWorldTimes().entrySet()) {
            String worldName = entry.getKey();
            GMTimes gmTimes = entry.getValue();

            if (!aliases.contains(worldName)) {
                addWorld(worldName);
            }

            String alias = aliases.getString(worldName);

            GMTimes aliasGMTimes = gmTimesPerAlias.getOrDefault(alias, new GMTimes());
            for (String gm : gms) {
                aliasGMTimes.addTime(gm, gmTimes.getTime(gm));
            }
            gmTimesPerAlias.put(alias, aliasGMTimes);
        }
        return gmTimesPerAlias;
    }

    public String getLongestWorldPlayed(Session session) {
        ConfigNode aliases = getAliasSection();

        if (!session.supports(SessionKeys.WORLD_TIMES)) {
            return locale.get().getString(HtmlLang.UNIT_NO_DATA);
        }
        WorldTimes worldTimes = session.getValue(SessionKeys.WORLD_TIMES).orElse(new WorldTimes());
        if (!session.supports(SessionKeys.END)) {
            return worldTimes.getCurrentWorld()
                    .map(currentWorld -> "Current: " + (aliases.contains(currentWorld) ? aliases.getString(currentWorld) : currentWorld))
                    .orElse("Current: " + locale.get().getString(GenericLang.UNAVAILABLE));
        }

        Map<String, Long> playtimePerAlias = getPlaytimePerAlias(worldTimes);
        long total = worldTimes.getTotal();
        // Prevent arithmetic error if 0
        if (total <= 0) {
            total = -1;
        }

        long longest = 0;
        String theWorld = "-";
        for (Map.Entry<String, Long> entry : playtimePerAlias.entrySet()) {
            String world = entry.getKey();
            long time = entry.getValue();
            if (time > longest) {
                longest = time;
                theWorld = world;
            }
        }

        double quotient = longest * 1.0 / total;

        return theWorld + " (" + percentageFormatter.get().apply(quotient) + ")";
    }
}
