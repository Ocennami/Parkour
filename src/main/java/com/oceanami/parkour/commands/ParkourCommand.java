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
            player.sendMessage(Component.text("/parkour restart").color(NamedTextColor.YELLOW).append(Component.text(" - Reset progress (không teleport).", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/parkour reset").color(NamedTextColor.YELLOW).append(Component.text(" - Reset về đầu (không teleport).", NamedTextColor.GRAY)));

            if (player.hasPermission("parkour.admin")) {
                player.sendMessage(Component.text("--- Lệnh Admin ---").color(NamedTextColor.RED));
                player.sendMessage(Component.text("/parkour create <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Tạo màn chơi mới.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour setstart <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm bắt đầu.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour setfinish <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm kết thúc.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour addcheckpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Thêm điểm checkpoint.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour setrestartpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm bắt đầu lại tùy chỉnh.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour setresetpoint <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Đặt điểm quay về điểm bắt đầu tùy chỉnh.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour save <tên>").color(NamedTextColor.YELLOW).append(Component.text(" - Lưu màn chơi.", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/parkour reload").color(NamedTextColor.YELLOW).append(Component.text(" - Tải lại cấu hình plugin.", NamedTextColor.GRAY)));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Player commands that don't require admin perms
        switch (subCommand) {
            case "restart":
                handleRestart(player);
                return true;
            case "reset":
                handleReset(player);
                return true;
        }

        if (!player.hasPermission("parkour.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        // Admin commands
        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "setstart" -> handleSetLocation(player, args, "START");
            case "setfinish" -> handleSetLocation(player, args, "FINISH");
            case "addcheckpoint" -> handleSetLocation(player, args, "CHECKPOINT");
            case "setrestartpoint" -> handleSetLocation(player, args, "CUSTOM_RESTART");
            case "setresetpoint" -> handleSetLocation(player, args, "CUSTOM_RESET");
            case "save" -> handleSave(player, args);
            case "reload" -> handleReload(player);
            default -> player.sendMessage(Component.text("Unknown subcommand. Use /parkour for help.").color(NamedTextColor.RED));
        }

        return true;
    }

    private void handleRestart(Player player) {
        if (!parkourManager.isPlaying(player)) {
            player.sendMessage(Component.text("You are not currently in a parkour course.").color(NamedTextColor.RED));
            return;
        }
        // parkourManager.restartPlayer(player); // Comment out dòng này
        player.sendMessage(Component.text("Please return to the start point manually.").color(NamedTextColor.YELLOW));
    }

    private void handleReset(Player player) {
        if (!parkourManager.isPlaying(player)) {
            player.sendMessage(Component.text("You are not currently in a parkour course.").color(NamedTextColor.RED));
            return;
        }
        // parkourManager.resetPlayerToStart(player); // Comment out dòng này
        player.sendMessage(Component.text("Please return to the start point manually.").color(NamedTextColor.YELLOW));
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

    private void handleReload(Player player) {
        plugin.reloadPluginConfig();
        player.sendMessage(Component.text("Parkour plugin configuration reloaded.").color(NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> playerCommands = Arrays.asList("restart", "reset");
            if (sender.hasPermission("parkour.admin")) {
                List<String> adminCommands = Arrays.asList("create", "setstart", "setfinish", "addcheckpoint", "save", "setrestartpoint", "setresetpoint", "reload");
                return Stream.concat(playerCommands.stream(), adminCommands.stream())
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            } else {
                return playerCommands.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
        }
        // You can add tab completion for course names here in the future
        return Collections.emptyList();
    }
}
