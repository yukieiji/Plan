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
package com.djrapitops.plan.settings.config.paths.key;

import com.djrapitops.plan.settings.config.ConfigNode;

import java.util.function.Predicate;

/**
 * Setting implementation for String value settings.
 *
 * @author AuroraLS3
 */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String path) {
        super(path, Boolean.class);
    }

    public BooleanSetting(String path, Predicate<Boolean> validator) {
        super(path, Boolean.class, validator);
    }

    @Override
    public Boolean getValueFrom(ConfigNode node) {
        return node.getNode(path).map(ConfigNode::getBoolean).orElse(false);
    }
}