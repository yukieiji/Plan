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
package com.djrapitops.plan.commands;

import com.djrapitops.plan.commands.subcommands.*;
import com.djrapitops.plan.commands.use.*;
import com.djrapitops.plan.gathering.importing.ImportSystem;
import com.djrapitops.plan.settings.Permissions;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.DeepHelpLang;
import com.djrapitops.plan.settings.locale.lang.HelpLang;
import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class PlanCommand {

    private final String commandName;
    private final ColorScheme colors;
    private final Confirmation confirmation;
    private final TabCompleteCache tabCompleteCache;

    private final LinkCommands linkCommands;
    private final RegistrationCommands registrationCommands;
    private final PluginStatusCommands statusCommands;
    private final DatabaseCommands databaseCommands;
    private final DataUtilityCommands dataUtilityCommands;

    private final Locale locale;
    private final ImportSystem importSystem;
    private final ErrorLogger errorLogger;

    private static final String DB_ARG_OPTIONS = "MySQL/SQLite";

    @Inject
    public PlanCommand(
            @Named("mainCommandName") String commandName,
            Locale locale,
            ColorScheme colors,
            Confirmation confirmation,
            TabCompleteCache tabCompleteCache,
            ImportSystem importSystem,
            LinkCommands linkCommands,
            RegistrationCommands registrationCommands,
            PluginStatusCommands statusCommands,
            DatabaseCommands databaseCommands,
            DataUtilityCommands dataUtilityCommands,
            ErrorLogger errorLogger
    ) {
        this.commandName = commandName;
        this.locale = locale;
        this.colors = colors;
        this.confirmation = confirmation;
        this.tabCompleteCache = tabCompleteCache;
        this.importSystem = importSystem;
        this.linkCommands = linkCommands;
        this.registrationCommands = registrationCommands;
        this.statusCommands = statusCommands;
        this.databaseCommands = databaseCommands;
        this.dataUtilityCommands = dataUtilityCommands;
        this.errorLogger = errorLogger;
    }

    private void handleException(RuntimeException error, CMDSender sender, Arguments arguments) {
        if (error instanceof IllegalArgumentException) {
            sender.send("§c" + error.getMessage());
        } else {
            errorLogger.warn(error, ErrorContext.builder().related(sender, arguments).build());
        }
    }

    public CommandWithSubcommands build() {
        CommandWithSubcommands command = CommandWithSubcommands.builder(locale)
                .alias(commandName)
                .colorScheme(colors)
                .subcommand(serverCommand())
                .subcommand(serversCommand())
                .subcommand(networkCommand())
                .subcommand(playerCommand())
                .subcommand(playersCommand())
                .subcommand(searchCommand())
                .subcommand(inGameCommand())
                .subcommand(jsonCommand())

                .subcommand(registerCommand())
                .subcommand(unregisterCommand())
                .subcommand(logoutCommand())
                .subcommand(webUsersCommand())

                .subcommand(acceptCommand())
                .subcommand(cancelCommand())

                .subcommand(infoCommand())
                .subcommand(reloadCommand())
                .subcommand(disableCommand())
                .subcommand(databaseCommand())

                .subcommand(exportCommand())
                .subcommand(importCommand())
                .exceptionHandler(this::handleException)
                .build();
        if (!"plan".equalsIgnoreCase(commandName)) {
            command.getAliases().add("planbungee");
            command.getAliases().add("planvelocity");
            command.getAliases().add("planproxy");
            command.getAliases().add("planp");
        }
        return command;
    }

    public List<String> serverNames(CMDSender sender, Arguments arguments) {
        String asString = arguments.concatenate(" ");
        return tabCompleteCache.getMatchingServerIdentifiers(asString);
    }

    private List<String> playerNames(CMDSender sender, Arguments arguments) {
        String asString = arguments.concatenate(" ");
        return tabCompleteCache.getMatchingPlayerIdentifiers(asString);
    }

    private Subcommand serverCommand() {
        return Subcommand.builder()
                .aliases("server", "analyze", "a", "analyse", "analysis")
                .optionalArgument(locale.getString(HelpLang.ARG_SERVER), locale.getString(HelpLang.DESC_ARG_SERVER_IDENTIFIER))
                .requirePermission(Permissions.SERVER)
                .description(locale.getString(HelpLang.SERVER))
                .inDepthDescription(locale.getString(DeepHelpLang.SERVER))
                .onCommand(linkCommands::onServerCommand)
                .onTabComplete(this::serverNames)
                .build();
    }

    private Subcommand serversCommand() {
        return Subcommand.builder()
                .aliases("servers", "serverlist", "listservers", "sl", "ls")
                .requirePermission(Permissions.SERVERS)
                .description(locale.getString(HelpLang.SERVERS))
                .inDepthDescription(locale.getString(DeepHelpLang.SERVERS))
                .onCommand(linkCommands::onServersCommand)
                .build();
    }

    private Subcommand networkCommand() {
        return Subcommand.builder()
                .aliases("network", "netw")
                .requirePermission(Permissions.NETWORK)
                .description(locale.getString(HelpLang.NETWORK))
                .inDepthDescription(locale.getString(DeepHelpLang.NETWORK))
                .onCommand(linkCommands::onNetworkCommand)
                .build();
    }

    private Subcommand playerCommand() {
        return Subcommand.builder()
                .aliases("player", "inspect")
                .optionalArgument(locale.getString(HelpLang.ARG_NAME_UUID), locale.getString(HelpLang.DESC_ARG_PLAYER_IDENTIFIER))
                .requirePermission(Permissions.PLAYER_SELF)
                .description(locale.getString(HelpLang.PLAYER))
                .inDepthDescription(locale.getString(DeepHelpLang.PLAYER))
                .onCommand(linkCommands::onPlayerCommand)
                .onTabComplete(this::playerNames)
                .build();
    }

    private Subcommand playersCommand() {
        return Subcommand.builder()
                .aliases("players", "pl", "playerlist", "list")
                .requirePermission(Permissions.PLAYER_OTHER)
                .description(locale.getString(HelpLang.PLAYERS))
                .inDepthDescription(locale.getString(DeepHelpLang.PLAYERS))
                .onCommand(linkCommands::onPlayersCommand)
                .build();
    }

    private Subcommand searchCommand() {
        return Subcommand.builder()
                .aliases("search")
                .requiredArgument(locale.getString(HelpLang.ARG_NAME_UUID), locale.getString(HelpLang.DESC_ARG_PLAYER_IDENTIFIER))
                .requirePermission(Permissions.SEARCH)
                .description(locale.getString(HelpLang.SEARCH))
                .inDepthDescription(locale.getString(DeepHelpLang.SEARCH))
                .onCommand(dataUtilityCommands::onSearch)
                .build();
    }

    private Subcommand inGameCommand() {
        return Subcommand.builder()
                .aliases("ingame", "qinspect")
                .optionalArgument(locale.getString(HelpLang.ARG_NAME_UUID), locale.getString(HelpLang.DESC_ARG_PLAYER_IDENTIFIER))
                .requirePermission(Permissions.INGAME_SELF)
                .description(locale.getString(HelpLang.INGAME))
                .inDepthDescription(locale.getString(DeepHelpLang.INGAME))
                .onCommand(dataUtilityCommands::onInGame)
                .onTabComplete(this::playerNames)
                .build();
    }

    private Subcommand registerCommand() {
        return Subcommand.builder()
                .aliases("register")
                .requirePermission(Permissions.REGISTER_SELF)
                .optionalArgument("--code " + locale.getString(HelpLang.ARG_CODE), locale.getString(HelpLang.DESC_ARG_CODE))
                .description(locale.getString(HelpLang.REGISTER))
                .inDepthDescription(locale.getString(DeepHelpLang.REGISTER))
                .onCommand(registrationCommands::onRegister)
                .onTabComplete((sender, arguments) -> arguments.isEmpty() ? Collections.singletonList("--code") : Collections.emptyList())
                .build();
    }

    private Subcommand unregisterCommand() {
        return Subcommand.builder()
                .aliases("unregister")
                .requirePermission(Permissions.UNREGISTER_SELF)
                .optionalArgument(locale.getString(HelpLang.ARG_USERNAME), locale.getString(HelpLang.DESC_ARG_USERNAME))
                .description(locale.getString(HelpLang.UNREGISTER))
                .inDepthDescription(locale.getString(DeepHelpLang.UNREGISTER))
                .onCommand((sender, arguments) -> registrationCommands.onUnregister(commandName, sender, arguments))
                .onTabComplete(this::webUserNames)
                .build();
    }

    private Subcommand logoutCommand() {
        return Subcommand.builder()
                .aliases("logout")
                .requirePermission(Permissions.LOGOUT_OTHER)
                .requiredArgument(locale.getString(HelpLang.ARG_USERNAME), locale.getString(HelpLang.DESC_ARG_USERNAME))
                .description(locale.getString(HelpLang.LOGOUT))
                .inDepthDescription(locale.getString(DeepHelpLang.LOGOUT))
                .onCommand(registrationCommands::onLogoutCommand)
                .onTabComplete(this::webUserNames)
                .build();
    }

    private List<String> webUserNames(CMDSender sender, Arguments arguments) {
        if (!sender.hasPermission(Permissions.UNREGISTER_OTHER)) {
            return Collections.emptyList();
        }

        String username = arguments.concatenate(" ");
        return tabCompleteCache.getMatchingUserIdentifiers(username);
    }

    private Subcommand acceptCommand() {
        return Subcommand.builder()
                .aliases("accept", "yes", "y")
                .onCommand(confirmation::onAcceptCommand)
                .build();
    }

    private Subcommand cancelCommand() {
        return Subcommand.builder()
                .aliases("cancel", "deny", "no", "n")
                .onCommand(confirmation::onCancelCommand)
                .build();
    }

    private Subcommand infoCommand() {
        return Subcommand.builder()
                .aliases("info")
                .requirePermission(Permissions.INFO)
                .description(locale.getString(HelpLang.INFO))
                .inDepthDescription(locale.getString(DeepHelpLang.INFO))
                .onCommand(statusCommands::onInfo)
                .build();
    }

    private Subcommand reloadCommand() {
        return Subcommand.builder()
                .aliases("reload")
                .requirePermission(Permissions.RELOAD)
                .description(locale.getString(HelpLang.RELOAD))
                .inDepthDescription(locale.getString(DeepHelpLang.RELOAD))
                .onCommand(statusCommands::onReload)
                .build();
    }

    private Subcommand disableCommand() {
        return Subcommand.builder()
                .aliases("disable")
                .requirePermission(Permissions.DISABLE)
                .optionalArgument(locale.getString(HelpLang.ARG_FEATURE), locale.getString(HelpLang.DESC_ARG_FEATURE, "kickcount"))
                .description(locale.getString(HelpLang.DISABLE))
                .inDepthDescription(locale.getString(DeepHelpLang.DISABLE))
                .onCommand(statusCommands::onDisable)
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? Collections.singletonList("kickcount") : Collections.emptyList()
                ).build();
    }

    private Subcommand webUsersCommand() {
        return Subcommand.builder()
                .aliases("users", "webusers", "web")
                .requirePermission(Permissions.USERS)
                .description(locale.getString(HelpLang.USERS))
                .inDepthDescription(locale.getString(DeepHelpLang.USERS))
                .onCommand(linkCommands::onWebUsersCommand)
                .build();
    }

    private Subcommand databaseCommand() {
        return CommandWithSubcommands.builder(locale)
                .aliases("db", "database")
                .optionalArgument(locale.getString(HelpLang.ARG_SUBCOMMAND), locale.getString(HelpLang.DESC_ARG_SUBCOMMAND))
                .colorScheme(colors)
                .subcommand(backupCommand())
                .subcommand(restoreCommand())
                .subcommand(moveCommand())
                .subcommand(hotswapCommand())
                .subcommand(clearCommand())
                .subcommand(removeCommand())
                .subcommand(uninstalledCommand())
                .requirePermission(Permissions.DATA_BASE)
                .description(locale.getString(HelpLang.DB))
                .inDepthDescription(locale.getString(DeepHelpLang.DB))
                .build();
    }

    private Subcommand backupCommand() {
        return Subcommand.builder()
                .aliases("backup")
                .requirePermission(Permissions.DATA_BACKUP)
                .optionalArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_BACKUP))
                .description(locale.getString(HelpLang.DB_BACKUP))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_BACKUP))
                .onCommand(databaseCommands::onBackup)
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? DBType.names() : Collections.emptyList()
                ).build();
    }

    private Subcommand restoreCommand() {
        return Subcommand.builder()
                .aliases("restore")
                .requirePermission(Permissions.DATA_RESTORE)
                .requiredArgument(locale.getString(HelpLang.ARG_BACKUP_FILE), locale.getString(HelpLang.DESC_ARG_BACKUP_FILE))
                .optionalArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_RESTORE))
                .description(locale.getString(HelpLang.DB_RESTORE))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_RESTORE))
                .onCommand((sender, arguments) -> databaseCommands.onRestore(commandName, sender, arguments))
                .onTabComplete(this::getBackupFilenames)
                .build();
    }

    private List<String> getBackupFilenames(CMDSender sender, Arguments arguments) {
        if (arguments.get(1).isPresent()) {
            return DBType.names();
        }
        Optional<String> firstArgument = arguments.get(0);
        if (!firstArgument.isPresent()) {
            return tabCompleteCache.getMatchingBackupFilenames(null);
        }
        String part = firstArgument.get();
        return tabCompleteCache.getMatchingBackupFilenames(part);
    }

    private Subcommand moveCommand() {
        return Subcommand.builder()
                .aliases("move")
                .requirePermission(Permissions.DATA_MOVE)
                .requiredArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_MOVE_FROM))
                .requiredArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_MOVE_TO))
                .description(locale.getString(HelpLang.DB_MOVE))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_MOVE))
                .onCommand((sender, arguments) -> databaseCommands.onMove(commandName, sender, arguments))
                .onTabComplete((sender, arguments) -> DBType.names())
                .build();
    }

    private Subcommand hotswapCommand() {
        return Subcommand.builder()
                .aliases("hotswap")
                .requirePermission(Permissions.DATA_HOTSWAP)
                .requiredArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_HOTSWAP))
                .description(locale.getString(HelpLang.DB_HOTSWAP))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_HOTSWAP))
                .onCommand(databaseCommands::onHotswap)
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? DBType.names() : Collections.emptyList()
                ).build();
    }

    private Subcommand clearCommand() {
        return Subcommand.builder()
                .aliases("clear")
                .requirePermission(Permissions.DATA_CLEAR)
                .requiredArgument(DB_ARG_OPTIONS, locale.getString(HelpLang.DESC_ARG_DB_REMOVE))
                .description(locale.getString(HelpLang.DB_CLEAR))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_CLEAR))
                .onCommand((sender, arguments) -> databaseCommands.onClear(commandName, sender, arguments))
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? DBType.names() : Collections.emptyList()
                ).build();
    }

    private Subcommand removeCommand() {
        return Subcommand.builder()
                .aliases("remove")
                .requirePermission(Permissions.DATA_REMOVE_PLAYER)
                .requiredArgument(locale.getString(HelpLang.ARG_NAME_UUID), locale.getString(HelpLang.DESC_ARG_PLAYER_IDENTIFIER_REMOVE))
                .description(locale.getString(HelpLang.DB_REMOVE))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_REMOVE))
                .onCommand((sender, arguments) -> databaseCommands.onRemove(commandName, sender, arguments))
                .onTabComplete(this::playerNames)
                .build();
    }

    private Subcommand uninstalledCommand() {
        return Subcommand.builder()
                .aliases("uninstalled")
                .requirePermission(Permissions.DATA_REMOVE_SERVER)
                .requiredArgument(locale.getString(HelpLang.ARG_SERVER), locale.getString(HelpLang.DESC_ARG_SERVER_IDENTIFIER))
                .description(locale.getString(HelpLang.DB_UNINSTALLED))
                .inDepthDescription(locale.getString(DeepHelpLang.DB_UNINSTALLED))
                .onCommand(databaseCommands::onUninstalled)
                .onTabComplete(this::serverNames)
                .build();
    }

    private Subcommand exportCommand() {
        return Subcommand.builder()
                .aliases("export")
                .requirePermission(Permissions.DATA_EXPORT)
                .optionalArgument(locale.getString(HelpLang.ARG_EXPORT_KIND), "players/server_json")
                .description(locale.getString(HelpLang.EXPORT))
                .inDepthDescription(locale.getString(DeepHelpLang.EXPORT))
                .onCommand(dataUtilityCommands::onExport)
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? Arrays.asList("players", "server_json") : Collections.emptyList()
                ).build();
    }

    private Subcommand importCommand() {
        List<String> importerNames = importSystem.getImporterNames();
        if (importerNames.isEmpty()) return null;
        return Subcommand.builder()
                .aliases("import")
                .requirePermission(Permissions.DATA_IMPORT)
                .optionalArgument(locale.getString(HelpLang.ARG_IMPORT_KIND), importerNames.toString())
                .description(locale.getString(HelpLang.IMPORT))
                .inDepthDescription(locale.getString(DeepHelpLang.IMPORT))
                .onCommand(dataUtilityCommands::onImport)
                .onTabComplete((sender, arguments) ->
                        arguments.isEmpty() ? importerNames : Collections.emptyList()
                ).build();
    }

    private Subcommand jsonCommand() {
        return Subcommand.builder()
                .aliases("json", "raw")
                .requirePermission(Permissions.JSON_SELF)
                .optionalArgument(locale.getString(HelpLang.ARG_NAME_UUID), locale.getString(HelpLang.DESC_ARG_PLAYER_IDENTIFIER))
                .description(locale.getString(HelpLang.JSON))
                .inDepthDescription(locale.getString(DeepHelpLang.JSON))
                .onCommand(linkCommands::onJson)
                .onTabComplete(this::playerNames)
                .build();
    }
}