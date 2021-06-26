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
package com.djrapitops.plan.delivery.web;

import com.djrapitops.plan.delivery.web.resolver.Resolver;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * ResolverService Implementation.
 *
 * @author AuroraLS3
 */
@Singleton
public class ResolverSvc implements ResolverService {

    private final List<Container> basicResolvers;
    private final List<Container> regexResolvers;

    @Inject
    public ResolverSvc() {
        basicResolvers = new ArrayList<>();
        regexResolvers = new ArrayList<>();
    }

    public void register() {
        ResolverService.Holder.set(this);
    }

    @Override
    public void registerResolver(String pluginName, String start, Resolver resolver) {
        basicResolvers.add(new Container(pluginName, checking -> checking.startsWith(start), resolver, start));
        Collections.sort(basicResolvers);
    }

    @Override
    public void registerResolverForMatches(String pluginName, Pattern pattern, Resolver resolver) {
        regexResolvers.add(new Container(pluginName, pattern.asPredicate(), resolver, pattern.pattern()));
        Collections.sort(regexResolvers);
    }

    @Override
    public Optional<Resolver> getResolver(String target) {
        for (Container container : basicResolvers) {
            if (container.matcher.test(target)) return Optional.of(container.resolver);
        }
        for (Container container : regexResolvers) {
            if (container.matcher.test(target)) return Optional.of(container.resolver);
        }
        return Optional.empty();
    }

    @Override
    public List<Resolver> getResolvers(String target) {
        List<Resolver> resolvers = new ArrayList<>();
        for (Container container : basicResolvers) {
            if (container.matcher.test(target)) resolvers.add(container.resolver);
        }
        for (Container container : regexResolvers) {
            if (container.matcher.test(target)) resolvers.add(container.resolver);
        }
        return resolvers;
    }

    public Optional<String> getPluginInChargeOf(String target) {
        for (Container container : basicResolvers) {
            if (container.matcher.test(target)) return Optional.of(container.plugin);
        }
        for (Container container : regexResolvers) {
            if (container.matcher.test(target)) return Optional.of(container.plugin);
        }
        return Optional.empty();
    }

    private static class Container implements Comparable<Container> {
        final String plugin;
        final Predicate<String> matcher;
        final Resolver resolver;
        final String sortBy;

        public Container(String plugin, Predicate<String> matcher, Resolver resolver, String sortBy) {
            this.plugin = plugin;
            this.matcher = matcher;
            this.resolver = resolver;
            this.sortBy = sortBy;
        }

        @Override
        public int compareTo(Container o) {
            // Longest first
            return Integer.compare(o.sortBy.length(), this.sortBy.length());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Container)) return false;
            Container container = (Container) o;
            return Objects.equals(plugin, container.plugin) &&
                    Objects.equals(matcher, container.matcher) &&
                    Objects.equals(resolver, container.resolver) &&
                    Objects.equals(sortBy, container.sortBy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(plugin, matcher, resolver, sortBy);
        }
    }
}