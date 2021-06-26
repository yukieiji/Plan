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
package com.djrapitops.plan.delivery.web.resolver;

import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utility Resolver for organizing resolution in a tree-like structure.
 * <p>
 * CompositeResolver removes first part of the target with {@link URIPath#omitFirst()}
 * before calling the child Resolvers.
 * <p>
 * Example: {@code resolverService.registerResolver("/test/", compositeResolver);}
 * The Resolvers added to CompositeResolver will be given Request with URIPath "/".
 *
 * @author AuroraLS3
 */
public final class CompositeResolver implements Resolver {

    private final List<String> prefixes;
    private final List<Function<Request, Optional<Response>>> resolvers;
    private final List<Predicate<Request>> canAccess;

    CompositeResolver() {
        this.prefixes = new ArrayList<>();
        this.resolvers = new ArrayList<>();
        this.canAccess = new ArrayList<>();
    }

    public static CompositeResolver.Builder builder() {
        return new Builder();
    }

    private Optional<Function<Request, Optional<Response>>> getResolver(URIPath target) {
        return target.getPart(0).flatMap(this::findResolver);
    }

    private Optional<Predicate<Request>> getAccessCheck(URIPath target) {
        return target.getPart(0).flatMap(this::findAccessCheck);
    }

    private Optional<Function<Request, Optional<Response>>> findResolver(String prefix) {
        for (int i = 0; i < prefixes.size(); i++) {
            if (prefixes.get(i).equals(prefix)) {
                return Optional.of(resolvers.get(i));
            }
        }
        return Optional.empty();
    }

    private Optional<Predicate<Request>> findAccessCheck(String prefix) {
        for (int i = 0; i < prefixes.size(); i++) {
            if (prefixes.get(i).equals(prefix)) {
                return Optional.of(canAccess.get(i));
            }
        }
        return Optional.empty();
    }

    void add(String prefix, Resolver resolver) {
        if (prefix == null) throw new IllegalArgumentException("Prefix can not be null");
        if (resolver == null) throw new IllegalArgumentException("Resolver can not be null");
        prefixes.add(prefix);
        resolvers.add(resolver::resolve);
        canAccess.add(resolver::canAccess);
    }

    void add(String prefix, Function<Request, Response> resolver, Predicate<Request> accessCheck) {
        if (prefix == null) throw new IllegalArgumentException("Prefix can not be null");
        if (resolver == null) {
            throw new IllegalArgumentException("Function<Request, Response> resolver can not be null");
        }
        if (accessCheck == null) throw new IllegalArgumentException("Predicate<Request> accessCheck can not be null");
        prefixes.add(prefix);
        resolvers.add(request -> Optional.ofNullable(resolver.apply(request)));
        canAccess.add(accessCheck);
    }

    @Override
    public boolean canAccess(Request request) {
        Request forThis = request.omitFirstInPath();
        return getAccessCheck(forThis.getPath())
                .map(resolver -> resolver.test(forThis))
                .orElse(true);
    }

    @Override
    public Optional<Response> resolve(Request request) {
        Request forThis = request.omitFirstInPath();
        return getResolver(forThis.getPath()).flatMap(resolver -> resolver.apply(forThis));
    }

    public static class Builder {
        private final CompositeResolver composite;

        private Builder() {
            this.composite = new CompositeResolver();
        }

        /**
         * Add a new resolver to the CompositeResolver.
         *
         * @param prefix   Start of the target (first part of the target string, eg "example" in "/example/target/", or "" in "/")
         * @param resolver Resolver to call for this target, {@link URIPath#omitFirst()} will be called for Resolver method calls.
         * @return this builder.
         */
        public Builder add(String prefix, Resolver resolver) {
            composite.add(prefix, resolver);
            return this;
        }

        /**
         * Add a new resolver to the CompositeResolver by using functional interfaces
         *
         * @param prefix      Start of the target (first part of the target string, eg "example" in "/example/target/", or "" in "/")
         * @param resolver    Resolver to call for this target, {@link URIPath#omitFirst()} will be called for Resolver method calls.
         * @param accessCheck Function for checking if request should be allowed (true, default) or forbidden (false).
         * @return this builder.
         */
        public Builder add(String prefix, Function<Request, Response> resolver, Predicate<Request> accessCheck) {
            composite.add(prefix, resolver, accessCheck);
            return this;
        }

        public CompositeResolver build() {
            return composite;
        }
    }
}