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
package com.djrapitops.plan.settings.locale.lang;

/**
 * {@link Lang} implementation for single words.
 *
 * @author AuroraLS3
 */
public enum GenericLang implements Lang {
    YES("plugin.generic.yes", "Positive", "Yes"),
    NO("plugin.generic.no", "Negative", "No"),
    UNKNOWN("plugin.generic.unknown", "Unknown", "Unknown"),
    UNAVAILABLE("plugin.generic.unavailable", "Unavailable", "Unavailable"),
    TODAY("plugin.generic.today", "Today", "'Today'"),
    YESTERDAY("plugin.generic.yesterday", "Yesterday", "'Yesterday'");

    private final String key;
    private final String identifier;
    private final String defaultValue;

    GenericLang(String key, String identifier, String defaultValue) {
        this.key = key;
        this.identifier = identifier;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getKey() { return key; }

    @Override
    public String getDefault() {
        return defaultValue;
    }
}