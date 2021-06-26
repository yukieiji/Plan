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

import com.djrapitops.plan.delivery.web.resource.WebResource;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.ResourceSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.PluginLang;
import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.storage.file.Resource;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;
import net.playeranalytics.plugin.server.PluginLogger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.TextStringBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;

/**
 * ResourceService implementation.
 *
 * @author AuroraLS3
 */
@Singleton
public class ResourceSvc implements ResourceService {

    public final Set<Snippet> snippets;
    private final PlanFiles files;
    private final ResourceSettings resourceSettings;
    private final Locale locale;
    private final PluginLogger logger;
    private final ErrorLogger errorLogger;

    @Inject
    public ResourceSvc(
            PlanFiles files,
            PlanConfig config,
            Locale locale,
            PluginLogger logger,
            ErrorLogger errorLogger
    ) {
        this.files = files;
        this.resourceSettings = config.getResourceSettings();
        this.locale = locale;
        this.logger = logger;
        this.errorLogger = errorLogger;
        this.snippets = new HashSet<>();
    }

    public void register() {
        Holder.set(this);
    }

    @Override
    public WebResource getResource(String pluginName, String fileName, Supplier<WebResource> source) {
        checkParams(pluginName, fileName, source);
        return applySnippets(pluginName, fileName, getTheResource(pluginName, fileName, source));
    }

    public void checkParams(String pluginName, String fileName, Supplier<WebResource> source) {
        if (pluginName == null || pluginName.isEmpty()) {
            throw new IllegalArgumentException("'pluginName' can't be '" + pluginName + "'!");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("'fileName' can't be '" + fileName + "'!");
        }
        if (source == null) {
            throw new IllegalArgumentException("'source' can't be null!");
        }
    }

    private WebResource applySnippets(String pluginName, String fileName, WebResource resource) {
        Map<Position, StringBuilder> byPosition = calculateSnippets(fileName);
        if (byPosition.isEmpty()) return resource;

        String html = applySnippets(resource, byPosition);
        return WebResource.create(html);
    }

    private String applySnippets(WebResource resource, Map<Position, StringBuilder> byPosition) {
        String html = resource.asString();
        if (html == null) {
            return "Error: Given resource did not support WebResource#asString method properly and returned 'null'";
        }

        StringBuilder toHead = byPosition.get(Position.PRE_CONTENT);
        if (toHead != null) {
            html = StringUtils.replaceOnce(html, "</head>", toHead.append("</head>").toString());
        }

        StringBuilder toBody = byPosition.get(Position.PRE_MAIN_SCRIPT);
        if (toBody != null) {
            if (StringUtils.contains(html, "<script id=\"mainScript\"")) {
                html = StringUtils.replaceOnce(html, "<script id=\"mainScript\"", toBody.append("<script id=\"mainScript\"").toString());
            } else {
                html = StringUtils.replaceOnce(html, "</body>", toBody.append("</body>").toString());
            }
        }

        StringBuilder toBodyEnd = byPosition.get(Position.AFTER_MAIN_SCRIPT);
        if (toBodyEnd != null) {
            html = StringUtils.replaceOnce(html, "</body>", toBodyEnd.append("</body>").toString());
        }

        return html;
    }

    private Map<Position, StringBuilder> calculateSnippets(String fileName) {
        Map<Position, StringBuilder> byPosition = new EnumMap<>(Position.class);
        for (Snippet snippet : snippets) {
            if (snippet.matches(fileName)) {
                byPosition.computeIfAbsent(snippet.position, k -> new StringBuilder()).append(snippet.content);
            }
        }
        return byPosition;
    }

    public WebResource getTheResource(String pluginName, String fileName, Supplier<WebResource> source) {
        try {
            if (resourceSettings.shouldBeCustomized(pluginName, fileName)) {
                return getOrWriteCustomized(fileName, source);
            }
        } catch (IOException e) {
            errorLogger.warn(e, ErrorContext.builder()
                    .whatToDo("Report this or provide " + fileName + " in " + files.getCustomizationDirectory())
                    .related("Fetching resource", "Of: " + pluginName, fileName).build());
        }
        // Return original by default
        return source.get();
    }

    public WebResource getOrWriteCustomized(String fileName, Supplier<WebResource> source) throws IOException {
        Optional<Resource> customizedResource = files.getCustomizableResource(fileName);
        if (customizedResource.isPresent()) {
            return readCustomized(customizedResource.get());
        } else {
            return writeCustomized(fileName, source);
        }
    }

    public WebResource readCustomized(Resource customizedResource) throws IOException {
        try {
            return customizedResource.asWebResource();
        } catch (UncheckedIOException readFail) {
            throw readFail.getCause();
        }
    }

    public WebResource writeCustomized(String fileName, Supplier<WebResource> source) throws IOException {
        WebResource original = source.get();
        byte[] bytes = original.asBytes();
        OpenOption[] overwrite = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        Path to = files.getCustomizationDirectory().resolve(fileName);
        Path dir = to.getParent();
        if (!Files.isSymbolicLink(dir)) Files.createDirectories(dir);
        Files.write(to, bytes, overwrite);
        return original;
    }

    @Override
    public void addScriptsToResource(String pluginName, String fileName, Position position, String... jsSources) {
        checkParams(pluginName, fileName, position, jsSources);

        String snippet = new TextStringBuilder("<script src=\"")
                .appendWithSeparators(jsSources, "\"></script><script src=\"")
                .append("\"></script>").build();
        snippets.add(new Snippet(pluginName, fileName, position, snippet));
        if (!"Plan".equals(pluginName)) {
            logger.info(locale.getString(PluginLang.API_ADD_RESOURCE_JS, pluginName, fileName, position.cleanName()));
        }
    }

    public void checkParams(String pluginName, String fileName, Position position, String[] jsSources) {
        if (pluginName == null || pluginName.isEmpty()) {
            throw new IllegalArgumentException("'pluginName' can't be '" + pluginName + "'!");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("'fileName' can't be '" + fileName + "'!");
        }
        if (!fileName.endsWith(".html")) {
            throw new IllegalArgumentException("'" + fileName + "' is not a .html file! Only html files can be added to.");
        }
        if (position == null) {
            throw new IllegalArgumentException("'position' can't be null!");
        }
        if (jsSources == null || jsSources.length == 0) {
            throw new IllegalArgumentException("Can't add snippets to resource without snippets!");
        }
    }

    @Override
    public void addStylesToResource(String pluginName, String fileName, Position position, String... cssSources) {
        checkParams(pluginName, fileName, position, cssSources);

        String snippet = new TextStringBuilder("<link href=\"")
                .appendWithSeparators(cssSources, "\" rel=\"stylesheet\"></link><link href=\"")
                .append("\" rel=\"stylesheet\">").build();
        snippets.add(new Snippet(pluginName, fileName, position, snippet));
        if (!"Plan".equals(pluginName)) {
            logger.info(locale.getString(PluginLang.API_ADD_RESOURCE_CSS, pluginName, fileName, position.cleanName()));
        }
    }

    private static class Snippet {
        private final String pluginName;
        private final String fileName;
        private final Position position;
        private final String content;

        public Snippet(String pluginName, String fileName, Position position, String content) {
            this.pluginName = pluginName;
            this.fileName = fileName;
            this.position = position;
            this.content = content;
        }

        public boolean matches(String fileName) {
            return fileName.equals(this.fileName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Snippet snippet = (Snippet) o;
            return Objects.equals(pluginName, snippet.pluginName) &&
                    Objects.equals(fileName, snippet.fileName) &&
                    position == snippet.position &&
                    Objects.equals(content, snippet.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pluginName, fileName, position, content);
        }
    }
}
