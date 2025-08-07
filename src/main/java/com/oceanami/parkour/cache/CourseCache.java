package com.oceanami.parkour.cache;

import com.oceanami.parkour.database.DatabaseManager;
import com.oceanami.parkour.model.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CourseCache {

    private final Map<String, Course> courseCache = new ConcurrentHashMap<>();
    private final DatabaseManager dbManager;
    private final Logger logger;

    public CourseCache(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    public void loadCourses() {
        String sql = "SELECT id, name, ready FROM courses";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                boolean ready = rs.getBoolean("ready");
                courseCache.put(name.toLowerCase(), new Course(id, name, ready));
            }
            logger.info("Loaded " + courseCache.size() + " parkour courses into cache.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load parkour courses from database!", e);
        }
    }

    public Optional<Course> getCourse(String name) {
        return Optional.ofNullable(courseCache.get(name.toLowerCase()));
    }

    public void addCourse(Course course) {
        courseCache.put(course.getName().toLowerCase(), course);
    }

    public boolean courseExists(String name) {
        return courseCache.containsKey(name.toLowerCase());
    }
}
