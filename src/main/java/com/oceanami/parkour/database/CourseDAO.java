package com.oceanami.parkour.database;

import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.manager.LocationCache;
import com.oceanami.parkour.model.Course;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CourseDAO {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final CourseCache courseCache;
    private final LocationCache locationCache;

    public CourseDAO(JavaPlugin plugin, DatabaseManager databaseManager, CourseCache courseCache, LocationCache locationCache) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.courseCache = courseCache;
        this.locationCache = locationCache;
    }

    public CompletableFuture<Void> createCourse(String courseName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO courses (name, ready) VALUES (?, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, courseName);
                pstmt.setBoolean(2, false);
                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int id = generatedKeys.getInt(1);
                            Course newCourse = new Course(id, courseName, false);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                courseCache.addCourse(newCourse);
                                future.complete(null);
                            });
                            return;
                        }
                    }
                }
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(null));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create course", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> setStartLocation(int courseId, String courseName, Location loc) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveLocation(courseId, "START", 0, loc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    locationCache.addStartLocation(courseName, loc);
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set start location", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> setFinishLocation(int courseId, String courseName, Location loc) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveLocation(courseId, "FINISH", 0, loc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    locationCache.addFinishLocation(courseName, loc);
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set finish location", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> addCheckpoint(int courseId, String courseName, int order, Location loc) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveLocation(courseId, "CHECKPOINT", order, loc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    locationCache.addCheckpoint(courseName, order, loc);
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add checkpoint", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> setCustomRestartPoint(int courseId, String courseName, Location loc) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveLocation(courseId, "CUSTOM_RESTART", 0, loc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    locationCache.addCustomRestartPoint(courseName, loc);
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set custom restart point", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> setCustomResetPoint(int courseId, String courseName, Location loc) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveLocation(courseId, "CUSTOM_RESET", 0, loc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    locationCache.addCustomResetPoint(courseName, loc);
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set custom reset point", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Integer> getCheckpointCount(int courseId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT COUNT(*) AS count FROM locations WHERE course_id = ? AND type = 'CHECKPOINT'";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, courseId);
                ResultSet rs = pstmt.executeQuery();
                int count = 0;
                if (rs.next()) {
                    count = rs.getInt("count");
                }
                future.complete(count);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get checkpoint count", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> setCourseReady(String courseName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE courses SET ready = ? WHERE name = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setBoolean(1, true);
                pstmt.setString(2, courseName);
                pstmt.executeUpdate();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    courseCache.getCourse(courseName).ifPresent(course -> course.setReady(true));
                    future.complete(null);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set course ready", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void saveLocation(int courseId, String type, int order, Location loc) throws SQLException {
        String sql = "INSERT OR REPLACE INTO locations (course_id, type, checkpoint_order, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, type);
            pstmt.setInt(3, order);
            pstmt.setString(4, loc.getWorld().getName());
            pstmt.setDouble(5, loc.getX());
            pstmt.setDouble(6, loc.getY());
            pstmt.setDouble(7, loc.getZ());
            pstmt.setFloat(8, loc.getYaw());
            pstmt.setFloat(9, loc.getPitch());
            pstmt.executeUpdate();
        }
    }
}
