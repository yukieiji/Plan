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
package com.djrapitops.plan.commands.subcommands;

import com.djrapitops.plan.commands.use.Arguments;
import com.djrapitops.plan.commands.use.CMDSender;
import com.djrapitops.plan.commands.use.ColorScheme;
import com.djrapitops.plan.delivery.formatting.Formatter;
import com.djrapitops.plan.delivery.formatting.Formatters;
import com.djrapitops.plan.exceptions.database.DBOpException;
import com.djrapitops.plan.identification.Identifiers;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.query.QuerySvc;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DatabaseSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.CommandLang;
import com.djrapitops.plan.settings.locale.lang.HelpLang;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.SQLiteDB;
import com.djrapitops.plan.storage.database.queries.objects.ServerQueries;
import com.djrapitops.plan.storage.database.transactions.BackupCopyTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemoveEverythingTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemovePlayerTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.SetServerAsUninstalledTransaction;
import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.utilities.logging.ErrorContext;
import com.djrapitops.plan.utilities.logging.ErrorLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Singleton
public class DatabaseCommands {

    private final Locale locale;
    private final Confirmation confirmation;
    private final ColorScheme colors;
    private final PlanFiles files;
    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final SQLiteDB.Factory sqliteFactory;
    private final QuerySvc queryService;
    private final ServerInfo serverInfo;
    private final Identifiers identifiers;
    private final PluginStatusCommands statusCommands;
    private final ErrorLogger errorLogger;

    private final Formatter<Long> timestamp;

    @Inject
    public DatabaseCommands(
            Locale locale,
            Confirmation confirmation,
            ColorScheme colors,
            PlanFiles files,
            PlanConfig config,
            DBSystem dbSystem,
            SQLiteDB.Factory sqliteFactory,
            QuerySvc queryService,
            ServerInfo serverInfo,
            Formatters formatters,
            Identifiers identifiers,
            PluginStatusCommands statusCommands,
            ErrorLogger errorLogger
    ) {
        this.locale = locale;
        this.confirmation = confirmation;
        this.colors = colors;
        this.files = files;
        this.config = config;
        this.dbSystem = dbSystem;
        this.sqliteFactory = sqliteFactory;
        this.queryService = queryService;
        this.serverInfo = serverInfo;
        this.identifiers = identifiers;
        this.statusCommands = statusCommands;
        this.errorLogger = errorLogger;

        this.timestamp = formatters.iso8601NoClockLong();
    }

    public void onBackup(CMDSender sender, Arguments arguments) {
        String dbName = arguments.get(0)
                .orElse(dbSystem.getDatabase().getType().getName())
                .toLowerCase();

        if (!DBType.exists(dbName)) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, dbName));
        }

        Database fromDB = dbSystem.getActiveDatabaseByName(dbName);
        if (fromDB.getState() != Database.State.OPEN) fromDB.init();

        performBackup(sender, arguments, dbName, fromDB);
        sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));
    }

    public void performBackup(CMDSender sender, Arguments arguments, String dbName, Database fromDB) {
        Database toDB = null;
        try {
            String timeStamp = timestamp.apply(System.currentTimeMillis());
            String fileName = dbName + "-backup-" + timeStamp;
            sender.send(locale.getString(CommandLang.DB_BACKUP_CREATE, fileName, dbName));
            toDB = sqliteFactory.usingFileCalled(fileName);
            toDB.init();
            toDB.executeTransaction(new BackupCopyTransaction(fromDB, toDB)).get();
        } catch (DBOpException | ExecutionException e) {
            errorLogger.error(e, ErrorContext.builder().related(sender, arguments).build());
        } catch (InterruptedException e) {
            toDB.close();
            Thread.currentThread().interrupt();
        } finally {
            if (toDB != null) {
                toDB.close();
            }
        }
    }

    public void onRestore(String mainCommand, CMDSender sender, Arguments arguments) {
        String backupDbName = arguments.get(0)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_REQ_ARGS, 1, "<" + locale.getString(HelpLang.ARG_BACKUP_FILE) + ">")));

        boolean containsDBFileExtension = backupDbName.endsWith(".db");
        File backupDBFile = files.getFileFromPluginFolder(backupDbName + (containsDBFileExtension ? "" : ".db"));

        if (!backupDBFile.exists()) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_FILE_NOT_FOUND, backupDBFile.getAbsolutePath()));
        }

        String dbName = arguments.get(1)
                .orElse(dbSystem.getDatabase().getType().getName())
                .toLowerCase();
        if (!DBType.exists(dbName)) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, dbName));
        }

        Database toDB = dbSystem.getActiveDatabaseByName(dbName);

        // Check against restoring from database.db as it is active database
        if (backupDbName.contains("database") && toDB instanceof SQLiteDB) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_SAME_DB));
        }

        if (toDB.getState() != Database.State.OPEN) toDB.init();

        if (sender.supportsChatEvents()) {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_OVERWRITE_DB, toDB.getType().getName(), backupDBFile.toPath().toString())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM))
                    .addPart("§2§l[\u2714]").command("/" + mainCommand + " accept").hover(locale.getString(CommandLang.CONFIRM_ACCEPT))
                    .addPart(" ")
                    .addPart("§4§l[\u2718]").command("/" + mainCommand + " cancel").hover(locale.getString(CommandLang.CONFIRM_DENY))
                    .send();
        } else {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_OVERWRITE_DB, toDB.getType().getName(), backupDBFile.toPath().toString())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM)).addPart("§a/" + mainCommand + " accept")
                    .addPart(" ")
                    .addPart("§c/" + mainCommand + " cancel")
                    .send();
        }

        confirmation.confirm(sender, choice -> {
            if (Boolean.TRUE.equals(choice)) {
                performRestore(sender, backupDBFile, toDB);
            } else {
                sender.send(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CANCELLED_DATA));
            }
        });
    }

    public void performRestore(CMDSender sender, File backupDBFile, Database toDB) {
        try {
            SQLiteDB fromDB = sqliteFactory.usingFile(backupDBFile);
            fromDB.init();

            sender.send(locale.getString(CommandLang.DB_WRITE, toDB.getType().getName()));
            toDB.executeTransaction(new BackupCopyTransaction(fromDB, toDB)).get();
            sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (DBOpException | ExecutionException e) {
            errorLogger.error(e, ErrorContext.builder().related(backupDBFile, toDB.getType(), toDB.getState()).build());
            sender.send(locale.getString(CommandLang.PROGRESS_FAIL, e.getMessage()));
        }
    }

    public void onMove(String mainCommand, CMDSender sender, Arguments arguments) {
        DBType fromDB = arguments.get(0).flatMap(DBType::getForName)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, arguments.get(0).orElse("<MySQL/SQLite>"))));

        DBType toDB = arguments.get(1).flatMap(DBType::getForName)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, arguments.get(0).orElse("<MySQL/SQLite>"))));

        if (fromDB == toDB) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_SAME_DB));
        }

        if (sender.supportsChatEvents()) {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_OVERWRITE_DB, toDB.getName(), fromDB.getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM))
                    .addPart("§2§l[\u2714]").command("/" + mainCommand + " accept").hover(locale.getString(CommandLang.CONFIRM_ACCEPT))
                    .addPart(" ")
                    .addPart("§4§l[\u2718]").command("/" + mainCommand + " cancel").hover(locale.getString(CommandLang.CONFIRM_DENY))
                    .send();
        } else {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_OVERWRITE_DB, toDB.getName(), fromDB.getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM)).addPart("§a/" + mainCommand + " accept")
                    .addPart(" ")
                    .addPart("§c/" + mainCommand + " cancel")
                    .send();
        }

        confirmation.confirm(sender, choice -> {
            if (Boolean.TRUE.equals(choice)) {
                performMove(sender, fromDB, toDB);
            } else {
                sender.send(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CANCELLED_DATA));
            }
        });
    }

    private void performMove(CMDSender sender, DBType fromDB, DBType toDB) {
        try {
            Database fromDatabase = dbSystem.getActiveDatabaseByType(fromDB);
            Database toDatabase = dbSystem.getActiveDatabaseByType(toDB);
            fromDatabase.init();
            toDatabase.init();

            sender.send(locale.getString(CommandLang.DB_WRITE, toDB.getName()));

            toDatabase.executeTransaction(new BackupCopyTransaction(fromDatabase, toDatabase)).get();

            sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));

            boolean movingToCurrentDB = toDatabase.getType() == dbSystem.getDatabase().getType();
            if (movingToCurrentDB) {
                sender.send(locale.getString(CommandLang.HOTSWAP_REMINDER, toDatabase.getType().getConfigName()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            errorLogger.error(e, ErrorContext.builder().related(sender, fromDB.getName() + "->" + toDB.getName()).build());
            sender.send(locale.getString(CommandLang.PROGRESS_FAIL, e.getMessage()));
        }
    }


    public void onClear(String mainCommand, CMDSender sender, Arguments arguments) {
        DBType fromDB = arguments.get(0).flatMap(DBType::getForName)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, arguments.get(0).orElse("<MySQL/SQLite>"))));

        if (sender.supportsChatEvents()) {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CLEAR_DB, fromDB.getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM))
                    .addPart("§2§l[\u2714]").command("/" + mainCommand + " accept").hover(locale.getString(CommandLang.CONFIRM_ACCEPT))
                    .addPart(" ")
                    .addPart("§4§l[\u2718]").command("/" + mainCommand + " cancel").hover(locale.getString(CommandLang.CONFIRM_DENY))
                    .send();
        } else {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CLEAR_DB, fromDB.getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM)).addPart("§a/" + mainCommand + " accept")
                    .addPart(" ")
                    .addPart("§c/" + mainCommand + " cancel")
                    .send();
        }

        confirmation.confirm(sender, choice -> {
            if (Boolean.TRUE.equals(choice)) {
                performClear(sender, fromDB);
            } else {
                sender.send(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CANCELLED_DATA));
            }
        });
    }

    private void performClear(CMDSender sender, DBType fromDB) {
        try {
            Database fromDatabase = dbSystem.getActiveDatabaseByType(fromDB);
            fromDatabase.init();

            sender.send(locale.getString(CommandLang.DB_REMOVAL, fromDB.getName()));

            fromDatabase.executeTransaction(new RemoveEverythingTransaction())
                    .get(); // Wait for completion
            queryService.dataCleared();
            sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));

            // Reload plugin to register the server into the database
            // Otherwise errors will start.
            statusCommands.onReload(sender);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (DBOpException | ExecutionException e) {
            sender.send(locale.getString(CommandLang.PROGRESS_FAIL, e.getMessage()));
            errorLogger.error(e, ErrorContext.builder().related(sender, fromDB.getName()).build());
        }
    }

    public void onRemove(String mainCommand, CMDSender sender, Arguments arguments) {
        String identifier = arguments.concatenate(" ");
        UUID playerUUID = identifiers.getPlayerUUID(identifier);
        if (playerUUID == null) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_PLAYER_NOT_FOUND, identifier));
        }

        Database database = dbSystem.getDatabase();

        if (sender.supportsChatEvents()) {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_REMOVE_PLAYER_DB, playerUUID, database.getType().getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM))
                    .addPart("§2§l[\u2714]").command("/" + mainCommand + " accept").hover(locale.getString(CommandLang.CONFIRM_ACCEPT))
                    .addPart(" ")
                    .addPart("§4§l[\u2718]").command("/" + mainCommand + " cancel").hover(locale.getString(CommandLang.CONFIRM_DENY))
                    .send();
        } else {
            sender.buildMessage()
                    .addPart(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_REMOVE_PLAYER_DB, playerUUID, database.getType().getName())).newLine()
                    .addPart(colors.getTertiaryColor() + locale.getString(CommandLang.CONFIRM)).addPart("§a/" + mainCommand + " accept")
                    .addPart(" ")
                    .addPart("§c/" + mainCommand + " cancel")
                    .send();
        }

        confirmation.confirm(sender, choice -> {
            if (Boolean.TRUE.equals(choice)) {
                performRemoval(sender, database, playerUUID);
            } else {
                sender.send(colors.getMainColor() + locale.getString(CommandLang.CONFIRM_CANCELLED_DATA));
            }
        });
    }

    private void performRemoval(CMDSender sender, Database database, UUID playerToRemove) {
        try {
            sender.send(locale.getString(CommandLang.DB_REMOVAL_PLAYER, playerToRemove, database.getType().getName()));

            queryService.playerRemoved(playerToRemove);
            database.executeTransaction(new RemovePlayerTransaction(playerToRemove))
                    .get(); // Wait for completion

            sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (DBOpException | ExecutionException e) {
            sender.send(locale.getString(CommandLang.PROGRESS_FAIL, e.getMessage()));
            errorLogger.error(e, ErrorContext.builder().related(sender, database.getType().getName(), playerToRemove).build());
        }
    }

    private void ensureDatabaseIsOpen() {
        Database.State dbState = dbSystem.getDatabase().getState();
        if (dbState != Database.State.OPEN) {
            throw new IllegalArgumentException(locale.getString(CommandLang.FAIL_DATABASE_NOT_OPEN, dbState.name()));
        }
    }

    public void onUninstalled(CMDSender sender, Arguments arguments) {
        ensureDatabaseIsOpen();
        String identifier = arguments.concatenate(" ");
        Server server = dbSystem.getDatabase()
                .query(ServerQueries.fetchServerMatchingIdentifier(identifier))
                .filter(s -> !s.isProxy())
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_SERVER_NOT_FOUND, identifier)));

        if (server.getUuid().equals(serverInfo.getServerUUID())) {
            throw new IllegalArgumentException(locale.getString(CommandLang.UNINSTALLING_SAME_SERVER));
        }

        dbSystem.getDatabase().executeTransaction(new SetServerAsUninstalledTransaction(server.getUuid()));
        sender.send(locale.getString(CommandLang.PROGRESS_SUCCESS));
        sender.send(locale.getString(CommandLang.DB_UNINSTALLED));
    }

    public void onHotswap(CMDSender sender, Arguments arguments) {
        DBType toDB = arguments.get(0).flatMap(DBType::getForName)
                .orElseThrow(() -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_INCORRECT_DB, arguments.get(0).orElse("<MySQL/SQLite>"))));

        try {
            Database database = dbSystem.getActiveDatabaseByType(toDB);
            database.init();

            if (database.getState() == Database.State.CLOSED) {
                return;
            }

            config.set(DatabaseSettings.TYPE, toDB.getName());
            config.save();
        } catch (DBOpException | IOException e) {
            errorLogger.warn(e, ErrorContext.builder().related(toDB).build());
            sender.send(locale.getString(CommandLang.PROGRESS_FAIL, e.getMessage()));
            return;
        }
        statusCommands.onReload(sender);
    }
}
