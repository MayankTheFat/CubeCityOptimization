package city.cube.optimization.storage;

import city.cube.optimization.config.PluginConfig;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite / MySQL backend. A single connection is reused and every write goes
 * through one daemon thread, so the server thread never waits on the database.
 */
public final class SqlStatsStorage implements StatsStorage {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PluginConfig.StorageSettings settings;
    private final File dataFolder;
    private final Logger logger;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CubeCity-Storage");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean ready = false;
    private volatile String failure = null;
    private Connection connection;

    public SqlStatsStorage(PluginConfig.StorageSettings settings, File dataFolder, Logger logger) {
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public void init() {
        try {
            String url = buildUrl();
            Class.forName(settings.mysql() ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC");
            synchronized (this) {
                connection = connect();
                createTables();
                prune();
            }
            ready = true;
            logger.info("Storage ready (" + describe() + ")");
        } catch (Exception err) {
            failure = err.getMessage();
            ready = false;
            logger.log(Level.WARNING, "Storage disabled: " + err.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        if (settings.mysql()) {
            return DriverManager.getConnection(buildUrl(), settings.username, settings.password);
        }
        return DriverManager.getConnection(buildUrl());
    }

    private String buildUrl() {
        if (settings.mysql()) {
            return "jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.database
                    + "?useSSL=" + settings.useSsl
                    + "&autoReconnect=true&characterEncoding=utf8&serverTimezone=UTC";
        }
        File file = new File(dataFolder, settings.sqliteFile);
        return "jdbc:sqlite:" + file.getAbsolutePath();
    }

    private void createTables() throws SQLException {
        boolean mysql = settings.mysql();
        String autoId = mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String text = "TEXT";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cco_snapshots ("
                    + "id " + autoId + ","
                    + "created_at BIGINT NOT NULL,"
                    + "tps DOUBLE NOT NULL,"
                    + "mspt DOUBLE NOT NULL,"
                    + "worlds INT NOT NULL,"
                    + "chunks INT NOT NULL,"
                    + "entities INT NOT NULL,"
                    + "hoppers INT NOT NULL,"
                    + "dispensers INT NOT NULL,"
                    + "tnt INT NOT NULL,"
                    + "mobs INT NOT NULL,"
                    + "counters " + text + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cco_actions ("
                    + "id " + autoId + ","
                    + "created_at BIGINT NOT NULL,"
                    + "action VARCHAR(64) NOT NULL,"
                    + "detail " + text + ")");

            // MySQL before 8.0.29 has no CREATE INDEX IF NOT EXISTS; a duplicate index is harmless.
            try {
                statement.executeUpdate("CREATE INDEX idx_cco_snapshots_time ON cco_snapshots (created_at)");
            } catch (SQLException ignored) {
                // already present
            }
            try {
                statement.executeUpdate("CREATE INDEX idx_cco_actions_time ON cco_actions (created_at)");
            } catch (SQLException ignored) {
                // already present
            }
        }
    }

    private void prune() throws SQLException {
        if (settings.retentionDays <= 0) {
            return;
        }
        long cutoff = Instant.now().minusSeconds(settings.retentionDays * 86_400L).toEpochMilli();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM cco_actions WHERE created_at < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        }
    }

    @Override
    public void record(StatsSnapshot snapshot) {
        if (!ready) {
            return;
        }
        worker.submit(() -> {
            try {
                synchronized (this) {
                    connection = ensureConnection();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO cco_snapshots (created_at, tps, mspt, worlds, chunks, entities,"
                                    + " hoppers, dispensers, tnt, mobs, counters)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                        statement.setLong(1, snapshot.createdAt());
                        statement.setDouble(2, snapshot.tps());
                        statement.setDouble(3, snapshot.mspt());
                        statement.setInt(4, snapshot.worlds());
                        statement.setInt(5, snapshot.chunks());
                        statement.setInt(6, snapshot.entities());
                        statement.setInt(7, snapshot.hoppers());
                        statement.setInt(8, snapshot.dispensers());
                        statement.setInt(9, snapshot.tnt());
                        statement.setInt(10, snapshot.mobs());
                        statement.setString(11, snapshot.countersJson());
                        statement.executeUpdate();
                    }
                }
            } catch (Exception err) {
                logger.log(Level.FINE, "Snapshot write failed: " + err.getMessage());
            }
        });
    }

    @Override
    public void action(String action, String detail) {
        if (!ready) {
            return;
        }
        worker.submit(() -> {
            try {
                synchronized (this) {
                    connection = ensureConnection();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO cco_actions (created_at, action, detail) VALUES (?,?,?)")) {
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setString(2, action);
                        statement.setString(3, detail);
                        statement.executeUpdate();
                    }
                }
            } catch (Exception err) {
                logger.log(Level.FINE, "Action write failed: " + err.getMessage());
            }
        });
    }

    @Override
    public List<String> recentActions(int limit) {
        List<String> out = new ArrayList<>();
        if (!ready) {
            return out;
        }
        try {
            synchronized (this) {
                connection = ensureConnection();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT created_at, action, detail FROM cco_actions ORDER BY id DESC LIMIT ?")) {
                    statement.setInt(1, Math.max(1, Math.min(50, limit)));
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            String when = TIME.format(Instant.ofEpochMilli(rows.getLong("created_at")));
                            String detail = rows.getString("detail");
                            out.add(when + " " + rows.getString("action")
                                    + (detail == null || detail.isBlank() ? "" : " — " + detail));
                        }
                    }
                }
            }
        } catch (Exception err) {
            logger.log(Level.FINE, "Action read failed: " + err.getMessage());
        }
        return out;
    }

    private Connection ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = connect();
        }
        return connection;
    }

    @Override
    public String describe() {
        if (settings.mysql()) {
            return "mysql " + settings.host + ":" + settings.port + "/" + settings.database;
        }
        return "sqlite " + settings.sqliteFile;
    }

    @Override
    public boolean ready() {
        return ready;
    }

    public String failure() {
        return failure;
    }

    @Override
    public void close() {
        ready = false;
        worker.shutdown();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {
                // shutting down
            }
        }
    }
}
