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
package com.djrapitops.plan.storage.database.transactions.events;

import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.webserver.configuration.WebserverConfiguration;
import com.djrapitops.plan.delivery.webserver.http.InternalRequest;
import com.djrapitops.plan.storage.database.sql.tables.AccessLogTable;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.Transaction;
import org.apache.commons.lang3.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class StoreRequestTransaction extends Transaction {

    private final WebserverConfiguration webserverConfiguration;

    private final InternalRequest internalRequest;
    private final Request request; // can be null
    private final Response response;

    public StoreRequestTransaction(WebserverConfiguration webserverConfiguration, InternalRequest internalRequest, Request request, Response response) {
        this.webserverConfiguration = webserverConfiguration;
        this.internalRequest = internalRequest;
        this.request = request;
        this.response = response;
    }

    @Override
    protected void performOperations() {
        execute(new ExecStatement(AccessLogTable.INSERT_NO_USER) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, internalRequest.getTimestamp());
                statement.setString(2, internalRequest.getAccessAddress(webserverConfiguration));
                String method = internalRequest.getMethod();
                statement.setString(3, method != null ? method : "?");
                statement.setString(4, getTruncatedURI());
                statement.setInt(5, response.getCode());
            }
        });
    }

    private String getTruncatedURI() {
        String uri = request != null ? request.getPath().asString() + request.getQuery().asString()
                : internalRequest.getRequestedURIString();
        if (uri == null) {
            uri = "non-HTTP request, missing URI";
        }
        return StringUtils.truncate(uri, 65000);
    }
}
