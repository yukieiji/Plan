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
package com.djrapitops.plan.delivery.rendering.html.structure;

import com.djrapitops.plan.delivery.rendering.html.icon.Icon;
import org.apache.commons.lang3.RegExUtils;

/**
 * Represents a structural HTML element that has Tabs on the top.
 *
 * @author AuroraLS3
 */
public class TabsElement {

    private final int pluginId;
    private final Tab[] tabs;

    public TabsElement(int pluginId, Tab... tabs) {
        this.pluginId = pluginId;
        this.tabs = tabs;
    }

    public String toHtmlFull() {
        String[] navAndContent = toHtml();
        return navAndContent[0] + navAndContent[1];
    }

    public String[] toHtml() {
        StringBuilder nav = new StringBuilder();
        StringBuilder content = new StringBuilder();

        nav.append("<ul class=\"nav nav-tabs tab-nav-right\" role=\"tablist\">");
        content.append("<div class=\"tab-content\">");
        boolean first = true;
        for (Tab tab : tabs) {
            String id = tab.getId(pluginId);
            String navHtml = tab.getNavHtml();
            String contentHtml = tab.getContentHtml();

            nav.append("<li role=\"presentation\" class=\"nav-item col-black\"")
                    .append("><a href=\"#").append(id).append("\" class=\"nav-link col-black").append(first ? " active" : "").append('"').append(" data-bs-toggle=\"tab\">")
                    .append(navHtml).append("</a></li>");
            content.append("<div role=\"tabpanel\" class=\"tab-pane fade").append(first ? " in active show" : "")
                    .append("\" id=\"").append(id).append("\">")
                    .append(contentHtml).append("</div>");
            first = false;
        }
        content.append("</div>");
        nav.append("</ul>");

        return new String[]{nav.toString(), content.toString()};
    }

    public static class Tab {

        private final Icon icon;
        private final String title;
        private final String contentHtml;

        public Tab(Icon icon, String title, String contentHtml) {
            this.icon = icon;
            this.title = title;
            this.contentHtml = contentHtml;
        }

        public String getNavHtml() {
            return icon.toHtml() + ' ' + title;
        }

        public String getContentHtml() {
            return contentHtml;
        }

        public String getId(int pluginId) {
            return "tab_" + pluginId + "_" + RegExUtils.removeAll(title, "[^a-zA-Z0-9]*").toLowerCase();
        }
    }
}
