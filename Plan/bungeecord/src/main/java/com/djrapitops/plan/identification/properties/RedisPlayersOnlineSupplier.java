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
package com.djrapitops.plan.identification.properties;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;

import java.util.function.IntSupplier;

/**
 * Players online supplier when using RedisBungee.
 *
 * @author AuroraLS3
 */
public class RedisPlayersOnlineSupplier implements IntSupplier {

    @Override
    public int getAsInt() {
        RedisBungeeAPI api = RedisBungee.getApi();
        try {
            return api != null ? api.getPlayerCount() : -1;
        } catch (NullPointerException e) {
            return -1;
        }
    }
}