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
package com.djrapitops.plan.delivery.webserver.auth;

import com.djrapitops.plan.delivery.domain.WebUser;
import com.djrapitops.plan.delivery.domain.auth.User;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.utilities.PassEncryptUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import utilities.TestConstants;
import utilities.mocks.objects.TestRunnableFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

class ActiveCookieStoreTest {

    private ActiveCookieStore underTest;
    private User user;

    @BeforeEach
    void createActiveCookieStore() {
        DBSystem dbSystem = Mockito.mock(DBSystem.class);
        Database db = Mockito.mock(Database.class);
        when(dbSystem.getDatabase()).thenReturn(db);

        underTest = new ActiveCookieStore(
                Mockito.mock(PlanConfig.class),
                dbSystem,
                new TestRunnableFactory(),
                Mockito.mock(Processing.class)
        );
        user = new User(TestConstants.PLAYER_ONE_NAME, "console", null, PassEncryptUtil.createHash("testPass"), 0, WebUser.getPermissionsForLevel(0));
    }

    @AfterEach
    void clearCookies() {
        underTest.disable();
    }

    @Test
    void cookiesAreStored() {
        String cookie = underTest.generateNewCookie(user);
        User matchingUser = underTest.checkCookie(cookie).orElseThrow(AssertionError::new);

        assertEquals(user, matchingUser);
    }

    @Test
    void cookiesAreRemoved() {
        String cookie = underTest.generateNewCookie(user);

        underTest.removeCookie(cookie);
        assertFalse(underTest.checkCookie(cookie).isPresent());
    }

    @Test
    void usersCookiesAreRemoved() {
        String cookie = underTest.generateNewCookie(user);

        ActiveCookieStore.removeUserCookie(user.getUsername());
        assertFalse(underTest.checkCookie(cookie).isPresent());
    }

}