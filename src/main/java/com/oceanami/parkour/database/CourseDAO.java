package com.oceanami.parkour.database;

import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.manager.LocationCache;
import com.oceanami.parkour.model.Course;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class CourseDAO {

    private final DatabaseManager databaseManager;
    private final CourseCache courseCache;
    private final LocationCache locationCache;

    public CourseDAO(DatabaseManager databaseManager, CourseCache courseCache, LocationCache locationCache) {
        this.databaseManager = databaseManager;
        this.courseCache = courseCache;
        this.locationCache = locationCache;
    }

    public void createCourse(String courseName) throws SQLException {
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
                        courseCache.addCourse(newCourse);
                    }
                }
            }
        }
    }

    public Optional<Integer> getCourseId(String courseName) throws SQLException {
        String sql = "SELECT id FROM courses WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, courseName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getInt("id"));
            }
        }
        return Optional.empty();
    }

    public void setStartLocation(int courseId, String courseName, Location loc) throws SQLException {
        saveLocation(courseId, "START", 0, loc);
        locationCache.addStartLocation(courseName, loc);
    }

    public void setFinishLocation(int courseId, String courseName, Location loc) throws SQLException {
        saveLocation(courseId, "FINISH", 0, loc);
        locationCache.addFinishLocation(courseName, loc);
    }

    public void addCheckpoint(int courseId, String courseName, int order, Location loc) throws SQLException {
        saveLocation(courseId, "CHECKPOINT", order, loc);
        locationCache.addCheckpoint(courseName, order, loc);
    }

    public void setCustomRestartPoint(int courseId, String courseName, Location loc) throws SQLException {
        saveLocation(courseId, "CUSTOM_RESTART", 0, loc);
        locationCache.addCustomRestartPoint(courseName, loc);
    }

    public void setCustomResetPoint(int courseId, String courseName, Location loc) throws SQLException {
        saveLocation(courseId, "CUSTOM_RESET", 0, loc);
        locationCache.addCustomResetPoint(courseName, loc);
    }

    public int getCheckpointCount(int courseId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM locations WHERE course_id = ? AND type = 'CHECKPOINT'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        return 0;
    }

    public void setCourseReady(String courseName) throws SQLException {
        String sql = "UPDATE courses SET ready = ? WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, true);
            pstmt.setString(2, courseName);
            pstmt.executeUpdate();
            courseCache.getCourse(courseName).ifPresent(course -> course.setReady(true));
        }
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
