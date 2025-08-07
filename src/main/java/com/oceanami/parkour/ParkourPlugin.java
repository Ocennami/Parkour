package com.oceanami.parkour;

import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.commands.ParkourCommand;
import com.oceanami.parkour.commands.RestartCommand;
import com.oceanami.parkour.database.CourseDAO;
import com.oceanami.parkour.database.DatabaseManager;
import com.oceanami.parkour.listeners.PlayerListener;
import com.oceanami.parkour.manager.LocationCache;
import com.oceanami.parkour.manager.ParkourManager;
import com.oceanami.parkour.manager.UIManager;
import com.oceanami.parkour.scheduler.ParkourTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ParkourPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ParkourManager parkourManager;
    private UIManager uiManager;
    private CourseDAO courseDAO;
    private CourseCache courseCache;
    private LocationCache locationCache;

    @Override
    public void onEnable() {
        getLogger().info("Parkour plugin is enabling...");

        // 1. Load configuration
        saveDefaultConfig();

        // 2. Setup managers and DAOs (order is important)
        this.databaseManager = new DatabaseManager(this);
        this.uiManager = new UIManager(this);
        this.locationCache = new LocationCache(this);
        this.courseCache = new CourseCache(this.databaseManager, getLogger());
        this.courseDAO = new CourseDAO(this.databaseManager, courseCache, locationCache);
        this.parkourManager = new ParkourManager(this, this.uiManager, courseCache, locationCache);

        // 3. Initialize database and load caches
        this.databaseManager.initializeDatabase();
        locationCache.loadLocations();
        courseCache.loadCourses();

        // 4. Register commands safely
        registerCommands();

        // 5. Register listeners with correct dependencies
        getServer().getPluginManager().registerEvents(new PlayerListener(this.parkourManager, locationCache, this), this);

        // 6. Start the scheduler task
        new ParkourTask(this).runTaskTimerAsynchronously(this, 0L, 1L); // Run every tick (50ms)

        getLogger().info("Parkour plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Parkour plugin has been disabled!");

        // Close database connection
        if (this.databaseManager != null) {
            this.databaseManager.closeConnection();
        }
    }

    public void reloadPluginConfig() {
        getLogger().info("Reloading Parkour plugin configuration...");

        // Reload the configuration file
        reloadConfig();

        // Re-initialize the managers to apply the new settings
        this.uiManager = new UIManager(this);
        this.locationCache = new LocationCache(this);
        this.courseCache = new CourseCache(this.databaseManager, getLogger());
        this.courseDAO = new CourseDAO(this.databaseManager, this.courseCache, this.locationCache);
        this.parkourManager = new ParkourManager(this, this.uiManager, this.courseCache, this.locationCache);

        // Reload caches
        this.locationCache.loadLocations();
        this.courseCache.loadCourses();

        // Re-register commands to use new manager instances
        registerCommands();

        getLogger().info("Parkour plugin configuration reloaded successfully!");
    }

    private void registerCommands() {
        PluginCommand parkourCommand = getCommand("parkour");
        if (parkourCommand != null) {
            parkourCommand.setExecutor(new ParkourCommand(this, this.parkourManager, this.courseDAO, this.courseCache, this.locationCache));
        } else {
            getLogger().log(Level.WARNING, "Command 'parkour' not found, please check plugin.yml");
        }

        PluginCommand restartCommand = getCommand("restart");
        if (restartCommand != null) {
            restartCommand.setExecutor(new RestartCommand(this.parkourManager));
        } else {
            getLogger().log(Level.WARNING, "Command 'restart' not found, please check plugin.yml");
        }
    }

    // Getters for other classes to use
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ParkourManager getParkourManager() { return parkourManager; }
    public UIManager getUiManager() { return uiManager; }
    public CourseDAO getCourseDAO() { return courseDAO; }
}
