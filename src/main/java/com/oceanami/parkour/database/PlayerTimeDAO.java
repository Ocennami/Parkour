package com.oceanami.parkour.database;

import com.oceanami.parkour.ParkourPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PlayerTimeDAO {

    private final DatabaseManager dbManager;
    private final CourseDAO courseDAO;

    public PlayerTimeDAO(ParkourPlugin plugin) {
        this.dbManager = plugin.getDatabaseManager();
        this.courseDAO = plugin.getCourseDAO();
    }

    public void savePlayerTime(Player player, String courseName, long timeMillis) {
        try {
            Optional<Integer> courseIdOpt = courseDAO.getCourseId(courseName);
            if (courseIdOpt.isEmpty()) {
                player.sendMessage(Component.text("Error: The course '" + courseName + "' no longer exists.").color(NamedTextColor.RED));
                return;
            }
            int courseId = courseIdOpt.get();

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
                    player.sendMessage(Component.text("Congratulations! You set a new personal best!").color(NamedTextColor.GREEN));
                }
            } else {
                player.sendMessage(Component.text("You did not beat your previous best time. Keep trying!").color(NamedTextColor.YELLOW));
            }
        } catch (SQLException e) {
            player.sendMessage(Component.text("Could not save your time due to a database error.").color(NamedTextColor.RED));
            e.printStackTrace();
        }
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
