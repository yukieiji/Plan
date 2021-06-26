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
package com.djrapitops.plan.delivery.webserver.resolver;

import com.djrapitops.plan.delivery.web.resolver.NoAuthResolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIPath;
import com.djrapitops.plan.delivery.webserver.ResponseFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Resolves all static resources for the pages.
 *
 * @author AuroraLS3
 */
@Singleton
public class StaticResourceResolver implements NoAuthResolver {

    private final ResponseFactory responseFactory;

    @Inject
    public StaticResourceResolver(ResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.ofNullable(getResponse(request));
    }

    private Response getResponse(Request request) {
        String resource = getPath(request).asString().substring(1);
        if (resource.endsWith(".css")) {
            return responseFactory.cssResponse(resource);
        }
        if (resource.endsWith(".js")) {
            return responseFactory.javaScriptResponse(resource);
        }
        if (resource.endsWith(".png")) {
            return responseFactory.imageResponse(resource);
        }
        if (StringUtils.endsWithAny(resource, ".woff", ".woff2", ".eot", ".ttf")) {
            return responseFactory.fontResponse(resource);
        }
        return null;
    }

    private URIPath getPath(Request request) {
        URIPath path = request.getPath();
        // Remove everything before /vendor /css /js or /img
        while (!path.getPart(0).map(part -> part.matches("(vendor|css|js|img)")).orElse(true)) {
            path = path.omitFirst();
        }
        return path;
    }
}