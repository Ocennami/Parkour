package com.oceanami.parkour.database;

import com.oceanami.parkour.ParkourPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class DatabaseManager {

    private final ParkourPlugin plugin;
    private Connection connection;

    public DatabaseManager(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        FileConfiguration config = plugin.getConfig();
        String storageType = config.getString("storage-type", "sqlite");

        if (storageType.equalsIgnoreCase("mysql")) {
            String host = config.getString("mysql.host");
            int port = config.getInt("mysql.port");
            String database = config.getString("mysql.database");
            String username = config.getString("mysql.username");
            String password = config.getString("mysql.password");
            
            synchronized (this) {
                 if (connection != null && !connection.isClosed()) {
                    return connection;
                }
                // Added autoReconnect=true for more stability
                connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true", username, password);
                plugin.getLogger().info("Successfully connected to MySQL database.");
            }
        } else { // Default to SQLite
            File databaseFile = new File(plugin.getDataFolder(), "parkour_data.db");
            if (!databaseFile.exists()) {
                try {
                    File dataFolder = plugin.getDataFolder();
                    if (!dataFolder.exists()) {
                        if (!dataFolder.mkdirs()) {
                            plugin.getLogger().warning("Failed to create plugin data folder.");
                        }
                    }
                    if (!databaseFile.createNewFile()) {
                        plugin.getLogger().severe("Could not create SQLite database file!");
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create SQLite database file!", e);
                }
            }
            
            synchronized (this) {
                if (connection != null && !connection.isClosed()) {
                    return connection;
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                plugin.getLogger().info("Successfully connected to SQLite database.");
            }
        }
        return connection;
    }

    public void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            // Table for parkour courses
            String coursesTable = "CREATE TABLE IF NOT EXISTS courses (" +
                                  "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                  "name VARCHAR(255) NOT NULL UNIQUE," +
                                  "ready BOOLEAN NOT NULL DEFAULT 0" +
                                  ");";
            stmt.execute(coursesTable);

            // Table for locations (start, finish, checkpoints)
            String locationsTable = "CREATE TABLE IF NOT EXISTS locations (" +
                                      "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                      "course_id INTEGER NOT NULL," +
                                      "type VARCHAR(16) NOT NULL, " + // START, FINISH, CHECKPOINT, CUSTOM_RESTART, CUSTOM_RESET
                                      "checkpoint_order INTEGER DEFAULT 0," +
                                      "world VARCHAR(255) NOT NULL," +
                                      "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL," +
                                      "yaw REAL NOT NULL, pitch REAL NOT NULL," +
                                      "FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE" +
                                      ");";
            stmt.execute(locationsTable);

            // Table for player best times
            String playerTimesTable = "CREATE TABLE IF NOT EXISTS parkour_times (" +
                                      "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                      "player_uuid VARCHAR(36) NOT NULL," +
                                      "player_name VARCHAR(16) NOT NULL," +
                                      "course_id INTEGER NOT NULL," +
                                      "time_millis BIGINT NOT NULL," +
                                      "UNIQUE(player_uuid, course_id)," +
                                      "FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE" +
                                      ");";
            stmt.execute(playerTimesTable);

            plugin.getLogger().info("Database tables initialized successfully.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database tables!", e);
        }
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close database connection.", e);
            }
        }
    }
}
