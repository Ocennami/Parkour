package com.oceanami.parkour.database;

import com.oceanami.parkour.ParkourPlugin;
import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.model.Course;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Handles persistence of player best times. All database operations are executed asynchronously,
 * and any interaction with the Bukkit API is dispatched back onto the main server thread.
 */
public class PlayerTimeDAO {

    private final ParkourPlugin plugin;
    private final DatabaseManager dbManager;
    private final CourseCache courseCache;

    public PlayerTimeDAO(ParkourPlugin plugin, CourseCache courseCache) {
        this.plugin = plugin;
        this.dbManager = plugin.getDatabaseManager();
        this.courseCache = courseCache;
    }

    public void savePlayerTime(Player player, String courseName, long timeMillis) {
        Optional<Course> courseOpt = courseCache.getCourse(courseName);
        if (courseOpt.isEmpty()) {
            player.sendMessage(Component.text("Error: The course '" + courseName + "' no longer exists.").color(NamedTextColor.RED));
            return;
        }
        int courseId = courseOpt.get().getId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<Long> existingTime = getPlayerTime(player.getUniqueId().toString(), courseId);

                if (existingTime.isEmpty() || timeMillis < existingTime.get()) {
                    String sql = "REPLACE INTO parkour_times (player_uuid, player_name, course_id, time_millis) VALUES (?, ?, ?, ?)";
                    try (Connection conn = dbManager.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, player.getUniqueId().toString());
                        pstmt.setString(2, player.getName());
                        pstmt.setInt(3, courseId);
                        pstmt.setLong(4, timeMillis);
                        pstmt.executeUpdate();
                    }
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("Congratulations! You set a new personal best!").color(NamedTextColor.GREEN))
                    );
                } else {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("You did not beat your previous best time. Keep trying!").color(NamedTextColor.YELLOW))
                    );
                }
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("Could not save your time due to a database error.").color(NamedTextColor.RED))
                );
                plugin.getLogger().log(Level.SEVERE, "Could not save player time", e);
            }
        });
    }

    private Optional<Long> getPlayerTime(String uuid, int courseId) throws SQLException {
        String sql = "SELECT time_millis FROM parkour_times WHERE player_uuid = ? AND course_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setInt(2, courseId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getLong("time_millis"));
            }
        }
        return Optional.empty();
    }
}
