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
package com.djrapitops.plan.gathering.domain;

import org.junit.jupiter.api.Test;
import utilities.RandomData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link PlayerKill}.
 *
 * @author AuroraLS3
 */
class PlayerKillTest {

    private final String weapon = RandomData.randomString(10);
    private final UUID testUUID = UUID.randomUUID();
    private final PlayerKill underTest = new PlayerKill(testUUID, testUUID, weapon, 100L);

    @Test
    void victimUUIDIsReturned() {
        assertEquals(testUUID, underTest.getVictim());
    }

    @Test
    void dateIsReturned() {
        assertEquals(100L, underTest.getDate());
    }

    @Test
    void weaponIsReturned() {
        assertEquals(weapon, underTest.getWeapon());
    }

    @Test
    void noVictimFound() {
        assertFalse(underTest.getVictimName().isPresent());
    }

    @Test
    void victimFound() {
        String expectedName = "Test Victim";
        PlayerKill underTest = new PlayerKill(testUUID, testUUID, weapon, 100L, expectedName);
        assertEquals("Test Victim", underTest.getVictimName().orElse("Unknown"));
    }
}
