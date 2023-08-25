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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import utilities.RandomData;
import utilities.TestConstants;

class ActiveSessionTest {

    @Test
    void noStackOverflowErrorFromFinishingSession() {
        Assertions.assertDoesNotThrow(() -> {
            ActiveSession session = RandomData.randomUnfinishedSession(TestConstants.SERVER_UUID, TestConstants.WORLDS, TestConstants.PLAYER_ONE_UUID, TestConstants.PLAYER_TWO_UUID);

            FinishedSession finishedSession = session.toFinishedSession(System.currentTimeMillis());

            session.hashCode(); // Error was here
            finishedSession.hashCode();
        });
    }

}