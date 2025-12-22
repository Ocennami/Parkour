package com.oceanami.parkour.commands;

import com.oceanami.parkour.ParkourPlugin;
import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.database.CourseDAO;
import com.oceanami.parkour.manager.LocationCache;
import com.oceanami.parkour.manager.ParkourManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private final ParkourManager parkourManager;
    private final CourseDAO courseDAO;
    private final CourseCache courseCache;
    private final LocationCache locationCache;
    private final ParkourPlugin plugin;

    public ParkourCommand(ParkourPlugin plugin, ParkourManager parkourManager, CourseDAO courseDAO, CourseCache courseCache, LocationCache locationCache) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
        this.courseDAO = courseDAO;
        this.courseCache = courseCache;
        this.locationCache = locationCache;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("--- Parkour Commands ---").color(NamedTextColor.GOLD));
            
            // Player commands - show only if has permission
            if (player.hasPermission("parkour.restart")) {
                player.sendMessage(Component.text("/parkour restart").color(NamedTextColor.YELLOW).append(Component.text(" - Teleport về start và restart timer.", NamedTextColor.GRAY)));
            }
            if (player.hasPermission("parkour.reset")) {
                player.sendMessage(Component.text("/parkour reset").color(NamedTextColor.YELLOW).append(Component.text(" - Xóa tất cả best times của bạn.", NamedTextColor.GRAY)));
            }
            if (player.hasPermission("parkour.top")) {
                player.sendMessage(Component.text("/parkour top <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Xem top 10 times.", NamedTextColor.GRAY)));
            }
            if (player.hasPermission("parkour.info")) {
                player.sendMessage(Component.text("/parkour info <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Xem thông tin course.", NamedTextColor.GRAY)));
            }

            // Admin commands - show header only if has any admin permission
            boolean hasAdminPerm = player.hasPermission("parkour.admin.create") || 
                                   player.hasPermission("parkour.admin.setlocation") ||
                                   player.hasPermission("parkour.admin.save") ||
                                   player.hasPermission("parkour.admin.delete") ||
                                   player.hasPermission("parkour.admin.reload");
            
            if (hasAdminPerm) {
                player.sendMessage(Component.text("--- Lệnh Admin ---").color(NamedTextColor.RED));
                if (player.hasPermission("parkour.admin.create")) {
                    player.sendMessage(Component.text("/parkour create <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Tạo màn chơi mới.", NamedTextColor.GRAY)));
                }
                if (player.hasPermission("parkour.admin.setlocation")) {
                    player.sendMessage(Component.text("/parkour setstart <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm bắt đầu.", NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("/parkour setfinish <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm kết thúc.", NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("/parkour addcheckpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Thêm điểm checkpoint.", NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("/parkour setrestartpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm bắt đầu lại.", NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("/parkour setresetpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm quay về start.", NamedTextColor.GRAY)));
                }
                if (player.hasPermission("parkour.admin.save")) {
                    player.sendMessage(Component.text("/parkour save <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Lưu màn chơi.", NamedTextColor.GRAY)));
                }
                if (player.hasPermission("parkour.admin.delete")) {
                    player.sendMessage(Component.text("/parkour delete <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Xóa màn chơi.", NamedTextColor.GRAY)));
                }
                if (player.hasPermission("parkour.admin.reload")) {
                    player.sendMessage(Component.text("/parkour reload").color(NamedTextColor.YELLOW).append(Component.text(" - Tải lại cấu hình.", NamedTextColor.GRAY)));
                }
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Player commands with individual permissions
        switch (subCommand) {
            case "restart":
                if (!player.hasPermission("parkour.restart")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                handleRestart(player);
                return true;
            case "reset":
                if (!player.hasPermission("parkour.reset")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                handleReset(player);
                return true;
            case "top":
                if (!player.hasPermission("parkour.top")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                handleTop(player, args);
                return true;
            case "info":
                if (!player.hasPermission("parkour.info")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                handleInfo(player, args);
                return true;
        }

        // Admin commands with individual permissions
        switch (subCommand) {
            case "create" -> {
                if (!player.hasPermission("parkour.admin.create")) {
                    player.sendMessage(Component.text("You don't have permission to create courses.").color(NamedTextColor.RED));
                    return true;
                }
                handleCreate(player, args);
            }
            case "setstart", "setfinish", "addcheckpoint", "setrestartpoint", "setresetpoint" -> {
                if (!player.hasPermission("parkour.admin.setlocation")) {
                    player.sendMessage(Component.text("You don't have permission to set locations.").color(NamedTextColor.RED));
                    return true;
                }
                String type = switch (subCommand) {
                    case "setstart" -> "START";
                    case "setfinish" -> "FINISH";
                    case "addcheckpoint" -> "CHECKPOINT";
                    case "setrestartpoint" -> "CUSTOM_RESTART";
                    case "setresetpoint" -> "CUSTOM_RESET";
                    default -> "";
                };
                handleSetLocation(player, args, type);
            }
            case "save" -> {
                if (!player.hasPermission("parkour.admin.save")) {
                    player.sendMessage(Component.text("You don't have permission to save courses.").color(NamedTextColor.RED));
                    return true;
                }
                handleSave(player, args);
            }
            case "delete" -> {
                if (!player.hasPermission("parkour.admin.delete")) {
                    player.sendMessage(Component.text("You don't have permission to delete courses.").color(NamedTextColor.RED));
                    return true;
                }
                handleDelete(player, args);
            }
            case "reload" -> {
                if (!player.hasPermission("parkour.admin.reload")) {
                    player.sendMessage(Component.text("You don't have permission to reload configuration.").color(NamedTextColor.RED));
                    return true;
                }
                handleReload(player);
            }
            default -> player.sendMessage(Component.text("Unknown subcommand. Use /parkour for help.").color(NamedTextColor.RED));
        }

        return true;
    }

    private void handleRestart(Player player) {
        if (!parkourManager.isPlaying(player)) {
            player.sendMessage(Component.text("You are not currently in a parkour course.").color(NamedTextColor.RED));
            return;
        }
        parkourManager.restartPlayer(player);
    }

    private void handleReset(Player player) {
        // Reset all player's best times from database
        boolean deleted = plugin.getCourseDAO() != null && 
            new com.oceanami.parkour.database.PlayerTimeDAO(plugin).deletePlayerTimes(player.getUniqueId().toString());
        
        if (deleted) {
            player.sendMessage(Component.text("All your parkour records have been deleted!").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("You have no parkour records to delete.").color(NamedTextColor.YELLOW));
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour create <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];
        try {
            if (courseCache.courseExists(courseName)) {
                player.sendMessage(Component.text("A course with that name already exists.").color(NamedTextColor.RED));
                return;
            }
            courseDAO.createCourse(courseName);
            player.sendMessage(Component.text("Successfully created parkour course: " + courseName).color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Now set the start and finish points, then use /parkour save " + courseName).color(NamedTextColor.YELLOW));
        } catch (SQLException e) {
            player.sendMessage(Component.text("An error occurred while creating the course.").color(NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "SQL Exception on course creation", e);
        }
    }

    private void handleSetLocation(Player player, String[] args, String type) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour " + args[0].toLowerCase() + " <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];
        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || targetBlock.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            player.sendMessage(Component.text("You must be looking at a Light Weighted Pressure Plate.").color(NamedTextColor.RED));
            return;
        }

        // SỬA CHÍNH TẠI ĐÂY - Không thêm 0.5, giữ nguyên vị trí block
        Location location = targetBlock.getLocation(); // Bỏ .add(0.5, 0, 0.5)
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());

        // Hoặc nếu muốn player đứng trên pressure plate:
        // Location location = targetBlock.getLocation().add(0, 1, 0); // Chỉ add Y

        try {
            var courseIdOptional = courseDAO.getCourseId(courseName);
            if (courseIdOptional.isEmpty()) {
                player.sendMessage(Component.text("Course not found: " + courseName).color(NamedTextColor.RED));
                return;
            }
            int courseId = courseIdOptional.get();

            switch (type) {
                case "START" -> {
                    courseDAO.setStartLocation(courseId, courseName, location);
                    player.sendMessage(Component.text("Start location set for " + courseName).color(NamedTextColor.GREEN));
                }
                case "FINISH" -> {
                    courseDAO.setFinishLocation(courseId, courseName, location);
                    player.sendMessage(Component.text("Finish location set for " + courseName).color(NamedTextColor.GREEN));
                }
                case "CHECKPOINT" -> {
                    int checkpointOrder = courseDAO.getCheckpointCount(courseId) + 1;
                    courseDAO.addCheckpoint(courseId, courseName, checkpointOrder, location);
                    player.sendMessage(Component.text("Added checkpoint #" + checkpointOrder + " for " + courseName).color(NamedTextColor.GREEN));
                }
                case "CUSTOM_RESTART" -> {
                    courseDAO.setCustomRestartPoint(courseId, courseName, location);
                    player.sendMessage(Component.text("Custom restart point set for " + courseName).color(NamedTextColor.GREEN));
                }
                case "CUSTOM_RESET" -> {
                    courseDAO.setCustomResetPoint(courseId, courseName, location);
                    player.sendMessage(Component.text("Custom reset point set for " + courseName).color(NamedTextColor.GREEN));
                }
            }

        } catch (SQLException e) {
            player.sendMessage(Component.text("An error occurred while setting the location.").color(NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "SQL Exception on setting location", e);
        }
    }

    private void handleSave(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour save <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];

        var courseOpt = courseCache.getCourse(courseName);
        if (courseOpt.isEmpty()) {
            player.sendMessage(Component.text("Course not found: " + courseName).color(NamedTextColor.RED));
            return;
        }

        if (courseOpt.get().isReady()) {
            player.sendMessage(Component.text("This course is already saved and ready to be played.").color(NamedTextColor.YELLOW));
            return;
        }

        boolean hasStart = locationCache.getStartLocation(courseName).isPresent();
        boolean hasFinish = locationCache.getFinishLocation(courseName).isPresent();

        if (!hasStart || !hasFinish) {
            player.sendMessage(Component.text("Cannot save course. It must have a start and a finish point set.").color(NamedTextColor.RED));
            if (!hasStart) player.sendMessage(Component.text("- Missing start point. Use /parkour setstart " + courseName).color(NamedTextColor.GRAY));
            if (!hasFinish) player.sendMessage(Component.text("- Missing finish point. Use /parkour setfinish " + courseName).color(NamedTextColor.GRAY));
            return;
        }

        try {
            courseDAO.setCourseReady(courseName);
            player.sendMessage(Component.text("Course '" + courseName + "' has been saved and is now open to players!").color(NamedTextColor.GREEN));
        } catch (SQLException e) {
            player.sendMessage(Component.text("An error occurred while saving the course.").color(NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "SQL Exception on saving course", e);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour delete <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];

        if (!courseCache.courseExists(courseName)) {
            player.sendMessage(Component.text("Course not found: " + courseName).color(NamedTextColor.RED));
            return;
        }

        try {
            boolean deleted = courseDAO.deleteCourse(courseName);
            if (deleted) {
                player.sendMessage(Component.text("Successfully deleted course: " + courseName).color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Failed to delete course: " + courseName).color(NamedTextColor.RED));
            }
        } catch (SQLException e) {
            player.sendMessage(Component.text("An error occurred while deleting the course.").color(NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "SQL Exception on course deletion", e);
        }
    }

    private void handleTop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour top <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];

        if (!courseCache.courseExists(courseName)) {
            player.sendMessage(Component.text("Course not found: " + courseName).color(NamedTextColor.RED));
            return;
        }

        try {
            var courseIdOpt = courseDAO.getCourseId(courseName);
            if (courseIdOpt.isEmpty()) {
                player.sendMessage(Component.text("Course not found.").color(NamedTextColor.RED));
                return;
            }

            var playerTimeDAO = new com.oceanami.parkour.database.PlayerTimeDAO(plugin);
            var topTimes = playerTimeDAO.getTopTimes(courseIdOpt.get(), 10);

            if (topTimes.isEmpty()) {
                player.sendMessage(Component.text("No times recorded for " + courseName + " yet.").color(NamedTextColor.YELLOW));
                return;
            }

            player.sendMessage(Component.text("--- Top 10: " + courseName + " ---").color(NamedTextColor.GOLD));
            for (var entry : topTimes) {
                String time = formatTime(entry.timeMillis());
                NamedTextColor color = switch (entry.rank()) {
                    case 1 -> NamedTextColor.GOLD;
                    case 2 -> NamedTextColor.GRAY;
                    case 3 -> NamedTextColor.DARK_RED;
                    default -> NamedTextColor.WHITE;
                };
                player.sendMessage(Component.text("#" + entry.rank() + " ", color)
                    .append(Component.text(entry.playerName(), NamedTextColor.YELLOW))
                    .append(Component.text(" - " + time, NamedTextColor.AQUA)));
            }
        } catch (SQLException e) {
            player.sendMessage(Component.text("Error loading leaderboard.").color(NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "SQL Exception on leaderboard", e);
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /parkour info <course_name>").color(NamedTextColor.RED));
            return;
        }
        String courseName = args[1];

        var courseOpt = courseCache.getCourse(courseName);
        if (courseOpt.isEmpty()) {
            player.sendMessage(Component.text("Course not found: " + courseName).color(NamedTextColor.RED));
            return;
        }

        var course = courseOpt.get();
        boolean hasStart = locationCache.getStartLocation(courseName).isPresent();
        boolean hasFinish = locationCache.getFinishLocation(courseName).isPresent();
        int checkpointCount = locationCache.getCheckpoints(courseName).map(java.util.Map::size).orElse(0);

        player.sendMessage(Component.text("--- Course: " + course.getName() + " ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Status: ").color(NamedTextColor.GRAY)
            .append(course.isReady() 
                ? Component.text("Ready", NamedTextColor.GREEN)
                : Component.text("Not Ready", NamedTextColor.RED)));
        player.sendMessage(Component.text("Start Point: ").color(NamedTextColor.GRAY)
            .append(hasStart ? Component.text("✓", NamedTextColor.GREEN) : Component.text("✗", NamedTextColor.RED)));
        player.sendMessage(Component.text("Finish Point: ").color(NamedTextColor.GRAY)
            .append(hasFinish ? Component.text("✓", NamedTextColor.GREEN) : Component.text("✗", NamedTextColor.RED)));
        player.sendMessage(Component.text("Checkpoints: ").color(NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(checkpointCount), NamedTextColor.YELLOW)));

        // Show player's best time if exists
        try {
            var courseIdOpt = courseDAO.getCourseId(courseName);
            if (courseIdOpt.isPresent()) {
                var playerTimeDAO = new com.oceanami.parkour.database.PlayerTimeDAO(plugin);
                var bestTime = playerTimeDAO.getPlayerBestTime(player.getUniqueId().toString(), courseIdOpt.get());
                if (bestTime.isPresent()) {
                    player.sendMessage(Component.text("Your Best Time: ").color(NamedTextColor.GRAY)
                        .append(Component.text(formatTime(bestTime.get()), NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(Component.text("Your Best Time: ").color(NamedTextColor.GRAY)
                        .append(Component.text("Not yet completed", NamedTextColor.YELLOW)));
                }
            }
        } catch (SQLException e) {
            // Ignore - just don't show best time
        }
    }

    private String formatTime(long millis) {
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long hundredths = (millis / 10) % 100;
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    private void handleReload(Player player) {
        plugin.reloadPluginConfig();
        player.sendMessage(Component.text("Parkour plugin configuration reloaded.").color(NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> playerCommands = Arrays.asList("restart", "reset", "top", "info");
            if (sender.hasPermission("parkour.admin")) {
                List<String> adminCommands = Arrays.asList("create", "setstart", "setfinish", "addcheckpoint", "save", "delete", "setrestartpoint", "setresetpoint", "reload");
                return Stream.concat(playerCommands.stream(), adminCommands.stream())
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            } else {
                return playerCommands.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
        }
        
        // Tab completion for course names (for admin commands that need a course name)
        if (args.length == 2 && sender.hasPermission("parkour.admin")) {
            List<String> courseCommands = Arrays.asList("setstart", "setfinish", "addcheckpoint", "save", "delete", "setrestartpoint", "setresetpoint", "top", "info");
            if (courseCommands.contains(args[0].toLowerCase())) {
                return courseCache.getAllCourseNames().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }
        
        return Collections.emptyList();
    }
}
