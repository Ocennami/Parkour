package com.oceanami.parkour.manager;

import com.oceanami.parkour.ParkourPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LocationCache {

    private final ParkourPlugin plugin;
    private final Map<String, Location> startLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> finishLocations = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Location>> checkpoints = new ConcurrentHashMap<>();
    private final Map<String, Location> customRestartPoints = new ConcurrentHashMap<>();
    private final Map<String, Location> customResetPoints = new ConcurrentHashMap<>();

    public LocationCache(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadLocations() {
        clearAllLocations();
        String sql = "SELECT c.name, l.type, l.checkpoint_order, l.world, l.x, l.y, l.z, l.yaw, l.pitch FROM locations l JOIN courses c ON l.course_id = c.id";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String courseName = rs.getString("name");
                String type = rs.getString("type");
                Location loc = new Location(
                        Bukkit.getWorld(rs.getString("world")),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                );

                switch (type) {
                    case "START" -> startLocations.put(courseName, loc);
                    case "FINISH" -> finishLocations.put(courseName, loc);
                    case "CHECKPOINT" -> {
                        int order = rs.getInt("checkpoint_order");
                        checkpoints.computeIfAbsent(courseName, k -> new ConcurrentHashMap<>()).put(order, loc);
                    }
                    case "CUSTOM_RESTART" -> customRestartPoints.put(courseName, loc);
                    case "CUSTOM_RESET" -> customResetPoints.put(courseName, loc);
                }
            }
            plugin.getLogger().info("Loaded " + (startLocations.size() + finishLocations.size() + checkpoints.size()) + " locations from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load locations from database", e);
        }
    }

    public void addStartLocation(String courseName, Location loc) {
        startLocations.put(courseName, loc);
    }

    public void addFinishLocation(String courseName, Location loc) {
        finishLocations.put(courseName, loc);
    }

    public void addCheckpoint(String courseName, int order, Location loc) {
        checkpoints.computeIfAbsent(courseName, k -> new ConcurrentHashMap<>()).put(order, loc);
    }

    public void addCustomRestartPoint(String courseName, Location loc) {
        customRestartPoints.put(courseName, loc);
    }

    public void addCustomResetPoint(String courseName, Location loc) {
        customResetPoints.put(courseName, loc);
    }

    public Optional<Location> getStartLocation(String courseName) {
        return Optional.ofNullable(startLocations.get(courseName));
    }

    public Optional<Location> getFinishLocation(String courseName) {
        return Optional.ofNullable(finishLocations.get(courseName));
    }

    public Optional<Location> getCheckpoint(String courseName, int order) {
        return Optional.ofNullable(checkpoints.get(courseName)).map(cps -> cps.get(order));
    }

    public Optional<Location> getCustomRestartPoint(String courseName) {
        return Optional.ofNullable(customRestartPoints.get(courseName));
    }

    public Optional<Location> getCustomResetPoint(String courseName) {
        return Optional.ofNullable(customResetPoints.get(courseName));
    }

    public Optional<PlateInfo> getPlateInfo(Location location) {
        // Check start locations
        for (Map.Entry<String, Location> entry : startLocations.entrySet()) {
            if (areLocationsEqual(entry.getValue(), location)) {
                return Optional.of(new PlateInfo(entry.getKey(), "START", 0));
            }
        }

        // Check finish locations
        for (Map.Entry<String, Location> entry : finishLocations.entrySet()) {
            if (areLocationsEqual(entry.getValue(), location)) {
                return Optional.of(new PlateInfo(entry.getKey(), "FINISH", 0));
            }
        }

        // Check checkpoints
        for (Map.Entry<String, Map<Integer, Location>> entry : checkpoints.entrySet()) {
            for (Map.Entry<Integer, Location> checkpointEntry : entry.getValue().entrySet()) {
                if (areLocationsEqual(checkpointEntry.getValue(), location)) {
                    return Optional.of(new PlateInfo(entry.getKey(), "CHECKPOINT", checkpointEntry.getKey()));
                }
            }
        }

        return Optional.empty();
    }

    private boolean areLocationsEqual(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            return false;
        }
        if (loc1.getWorld() == null || loc2.getWorld() == null) {
            return false; // Cannot compare if worlds are not set
        }
        return loc1.getWorld().getUID().equals(loc2.getWorld().getUID()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }


    public void clearAllLocations() {
        startLocations.clear();
        finishLocations.clear();
        checkpoints.clear();
        customRestartPoints.clear();
        customResetPoints.clear();
    }
}
