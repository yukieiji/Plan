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
package com.djrapitops.plan.extension.implementation.storage.queries;

import com.djrapitops.plan.extension.ElementOrder;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.implementation.TabInformation;
import com.djrapitops.plan.extension.implementation.results.ExtensionData;
import com.djrapitops.plan.extension.implementation.results.ExtensionDescription;
import com.djrapitops.plan.extension.implementation.results.ExtensionDoubleData;
import com.djrapitops.plan.extension.implementation.results.ExtensionTabData;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.SQLDB;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Query for selecting a percentage of true for each boolean provided for players.
 * <p>
 * Returns Map: PluginID - {@link ExtensionData.Builder}.
 * <p>
 * How it is done:
 * 1. Query count of boolean values
 * 2. Query count of boolean=true
 * 3. Combine with provider information
 * 4. Map into ExtensionData objects by PluginID, one per ID
 *
 * @author AuroraLS3
 */
public class ExtensionAggregateBooleansQuery implements Query<Map<Integer, ExtensionData.Builder>> {

    private final ServerUUID serverUUID;

    public ExtensionAggregateBooleansQuery(ServerUUID serverUUID) {
        this.serverUUID = serverUUID;
    }

    @Override
    public Map<Integer, ExtensionData.Builder> executeQuery(SQLDB db) {
        String selectTrueBooleans = SELECT +
                ExtensionPlayerValueTable.PROVIDER_ID +
                ",COUNT(1) as positive" +
                FROM + ExtensionPlayerValueTable.TABLE_NAME +
                WHERE + ExtensionPlayerValueTable.BOOLEAN_VALUE + "=?" +
                GROUP_BY + ExtensionPlayerValueTable.PROVIDER_ID;

        String selectBooleanCount = SELECT +
                ExtensionPlayerValueTable.PROVIDER_ID +
                ",COUNT(1) as total" +
                FROM + ExtensionPlayerValueTable.TABLE_NAME +
                WHERE + ExtensionPlayerValueTable.BOOLEAN_VALUE + IS_NOT_NULL +
                GROUP_BY + ExtensionPlayerValueTable.PROVIDER_ID;

        String sql = SELECT +
                "b1.total as total," +
                "b2.positive as positive," +
                "p1." + ExtensionProviderTable.PLUGIN_ID + " as plugin_id," +
                "p1." + ExtensionProviderTable.PROVIDER_NAME + " as provider_name," +
                "p1." + ExtensionProviderTable.TEXT + " as text," +
                "p1." + ExtensionProviderTable.DESCRIPTION + " as description," +
                "p1." + ExtensionProviderTable.PRIORITY + " as provider_priority," +
                "p1." + ExtensionProviderTable.IS_PLAYER_NAME + " as is_player_name," +
                "t1." + ExtensionTabTable.TAB_NAME + " as tab_name," +
                "t1." + ExtensionTabTable.TAB_PRIORITY + " as tab_priority," +
                "t1." + ExtensionTabTable.ELEMENT_ORDER + " as element_order," +
                "i1." + ExtensionIconTable.ICON_NAME + " as provider_icon_name," +
                "i1." + ExtensionIconTable.FAMILY + " as provider_icon_family," +
                "i1." + ExtensionIconTable.COLOR + " as provider_icon_color," +
                "i2." + ExtensionIconTable.ICON_NAME + " as tab_icon_name," +
                "i2." + ExtensionIconTable.FAMILY + " as tab_icon_family," +
                "i2." + ExtensionIconTable.COLOR + " as tab_icon_color" +
                FROM + '(' + selectBooleanCount + ") b1" +
                INNER_JOIN + ExtensionProviderTable.TABLE_NAME + " p1 on p1." + ExtensionProviderTable.ID + "=b1." + ExtensionPlayerValueTable.PROVIDER_ID +
                INNER_JOIN + ExtensionPluginTable.TABLE_NAME + " e1 on p1." + ExtensionProviderTable.PLUGIN_ID + "=e1." + ExtensionPluginTable.ID +
                LEFT_JOIN + '(' + selectTrueBooleans + ") b2 on b2." + ExtensionPlayerValueTable.PROVIDER_ID + "=b1." + ExtensionPlayerValueTable.PROVIDER_ID +
                LEFT_JOIN + ExtensionTabTable.TABLE_NAME + " t1 on t1." + ExtensionTabTable.ID + "=p1." + ExtensionProviderTable.TAB_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i1 on i1." + ExtensionIconTable.ID + "=p1." + ExtensionProviderTable.ICON_ID +
                LEFT_JOIN + ExtensionIconTable.TABLE_NAME + " i2 on i2." + ExtensionIconTable.ID + "=p1." + ExtensionTabTable.ICON_ID +
                WHERE + ExtensionPluginTable.SERVER_UUID + "=?" +
                AND + "p1." + ExtensionProviderTable.HIDDEN + "=?";

        return db.query(new QueryStatement<Map<Integer, ExtensionData.Builder>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setBoolean(1, true); // selectTrueBooleans parameter
                statement.setString(2, serverUUID.toString());
                statement.setBoolean(3, false); // Don't select hidden values
            }

            @Override
            public Map<Integer, ExtensionData.Builder> processResults(ResultSet set) throws SQLException {
                return extractTabDataByPluginID(set).toExtensionDataByPluginID();
            }
        });
    }

    private QueriedTabData extractTabDataByPluginID(ResultSet set) throws SQLException {
        QueriedTabData tabData = new QueriedTabData();

        while (set.next()) {
            int pluginID = set.getInt("plugin_id");
            String tabName = Optional.ofNullable(set.getString("tab_name")).orElse("");
            ExtensionTabData.Builder extensionTab = tabData.getTab(pluginID, tabName, () -> extractTabInformation(tabName, set));

            ExtensionDescription extensionDescription = extractDescription(set);
            extractAndPutDataTo(extensionTab, extensionDescription, set);
        }
        return tabData;
    }

    private TabInformation extractTabInformation(String tabName, ResultSet set) throws SQLException {
        Optional<Integer> tabPriority = Optional.of(set.getInt("tab_priority"));
        if (set.wasNull()) {
            tabPriority = Optional.empty();
        }
        Optional<ElementOrder[]> elementOrder = Optional.ofNullable(set.getString(ExtensionTabTable.ELEMENT_ORDER)).map(ElementOrder::deserialize);

        Icon tabIcon = extractTabIcon(set);

        return new TabInformation(
                tabName,
                tabIcon,
                elementOrder.orElse(ElementOrder.values()),
                tabPriority.orElse(100)
        );
    }

    private void extractAndPutDataTo(ExtensionTabData.Builder extensionTab, ExtensionDescription description, ResultSet set) throws SQLException {
        double percentageValue = percentage(set.getInt("positive"), set.getInt("total"));
        extensionTab.putPercentageData(new ExtensionDoubleData(description, percentageValue));
    }

    private double percentage(double first, double second) {
        if (first == 0.0 || second == 0.0) {
            return 0.0;
        }
        return first / second;
    }

    private ExtensionDescription extractDescription(ResultSet set) throws SQLException {
        String name = set.getString("provider_name") + "_aggregate";
        String text = set.getString(ExtensionProviderTable.TEXT) + " / Players";
        String description = set.getString(ExtensionProviderTable.DESCRIPTION);
        int priority = set.getInt("provider_priority");

        String iconName = set.getString("provider_icon_name");
        Family family = Family.getByName(set.getString("provider_icon_family")).orElse(Family.SOLID);
        Color color = Color.getByName(set.getString("provider_icon_color")).orElse(Color.NONE);
        Icon icon = new Icon(family, iconName, color);

        return new ExtensionDescription(name, text, description, icon, priority);
    }

    private Icon extractTabIcon(ResultSet set) throws SQLException {
        Optional<String> iconName = Optional.ofNullable(set.getString("tab_icon_name"));
        if (iconName.isPresent()) {
            Family iconFamily = Family.getByName(set.getString("tab_icon_family")).orElse(Family.SOLID);
            Color iconColor = Color.getByName(set.getString("tab_icon_color")).orElse(Color.NONE);
            return new Icon(iconFamily, iconName.get(), iconColor);
        } else {
            return TabInformation.defaultIcon();
        }
    }
}