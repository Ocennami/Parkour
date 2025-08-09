package com.oceanami.parkour.listeners;

import com.oceanami.parkour.ParkourPlugin;
import com.oceanami.parkour.manager.LocationCache;
import com.oceanami.parkour.manager.ParkourManager;
import com.oceanami.parkour.manager.PlateInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final ParkourManager parkourManager;
    private final LocationCache locationCache;
    private final ParkourPlugin plugin;
    private final Map<UUID, BukkitRunnable> playerTasks = new HashMap<>();
    private final Map<UUID, Long> startGracePeriod = new HashMap<>();
    private final Map<UUID, Long> interactionCooldown = new HashMap<>();


    // AFK Tracking
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Boolean> isPaused = new HashMap<>();
    private final Map<UUID, Long> totalPausedTime = new HashMap<>();
    private final Map<UUID, Long> pauseSystemTime = new HashMap<>();


    public PlayerListener(ParkourManager parkourManager, LocationCache locationCache, ParkourPlugin plugin) {
        this.parkourManager = parkourManager;
        this.locationCache = locationCache;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) return;

        Optional<PlateInfo> plateInfoOptional = locationCache.getPlateInfo(block.getLocation());
        if (plateInfoOptional.isEmpty()) return;

        PlateInfo plateInfo = plateInfoOptional.get();

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ParkourManager.ParkourSession session = parkourManager.getSession(player);
        String courseName = plateInfo.courseName();


        boolean inCorrectCourse = session != null && session.courseName().equals(courseName);

        switch (plateInfo.type()) {
            case "START":
                long now = System.currentTimeMillis();
                if (interactionCooldown.getOrDefault(player.getUniqueId(), 0L) > now - 2000L) { // 2 second cooldown
                    return;
                }

                if (session != null) {
                    // Always end the current session, whether it's the same course or a different one.
                    // This handles the "restart" case.
                    parkourManager.endSession(player, false);
                    stopScoreboard(player);
                }

                parkourManager.startSession(player, courseName);
                player.sendMessage(Component.text("Course started!").color(NamedTextColor.GREEN));
                
                startGracePeriod.put(playerUUID, System.currentTimeMillis());
                startScoreboard(player);
                interactionCooldown.put(player.getUniqueId(), now);
                break;
            case "CHECKPOINT":
                if (inCorrectCourse) {
                    if (plateInfo.checkpointOrder() > session.lastCheckpoint()) {
                        parkourManager.updateCheckpoint(player, plateInfo.checkpointOrder());
                        player.sendMessage(Component.text("Checkpoint #", NamedTextColor.AQUA)
                                .append(Component.text(String.valueOf(plateInfo.checkpointOrder()), NamedTextColor.YELLOW))
                                .append(Component.text(" reached!", NamedTextColor.AQUA)));
                    }
                }
                break;
            case "FINISH":
                if (inCorrectCourse) {
                    long timePaused = totalPausedTime.getOrDefault(player.getUniqueId(), 0L);
                    long timeTaken = (System.currentTimeMillis() - session.startTime() - timePaused) / 1000;
                    parkourManager.endSession(player, true, timePaused);
                    stopScoreboard(player);

                    Component mainTitle = Component.text("Course Completed!", NamedTextColor.GREEN);
                    Component subtitle = Component.text("Time: " + timeTaken + "s", NamedTextColor.YELLOW);
                    Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1));
                    Title title = Title.title(mainTitle, subtitle, times);
                    player.showTitle(title);
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (startGracePeriod.containsKey(playerUUID)) {
            if (System.currentTimeMillis() - startGracePeriod.get(playerUUID) > 2000) { // 2-second grace period
                startGracePeriod.remove(playerUUID);
            }
            return; // Don't check for falls during grace period
        }


        if (!parkourManager.isPlaying(player)) return;

        lastMoveTime.put(playerUUID, System.currentTimeMillis());

        if (isPaused.getOrDefault(playerUUID, false)) {
            isPaused.put(playerUUID, false);
            long pausedDuration = System.currentTimeMillis() - pauseSystemTime.get(playerUUID);
            totalPausedTime.compute(playerUUID, (k, v) -> (v == null ? 0 : v) + pausedDuration);
            player.sendMessage(Component.text("Timer resumed!", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (parkourManager.isPlaying(player)) {
            ParkourManager.ParkourSession session = parkourManager.getSession(player);
            if (session != null) {
                event.setRespawnLocation(session.lastCheckpointLocation());
                player.sendMessage(Component.text("Returned to your last checkpoint.", NamedTextColor.YELLOW));
            }
        }
    }


    private void startScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("parkour_time", Criteria.DUMMY, Component.text("Time", NamedTextColor.YELLOW, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);

        lastMoveTime.put(playerUUID, System.currentTimeMillis());
        isPaused.put(playerUUID, false);
        totalPausedTime.put(playerUUID, 0L);
        pauseSystemTime.put(playerUUID, 0L);


        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                ParkourManager.ParkourSession session = parkourManager.getSession(player);
                if (session == null) {
                    this.cancel();
                    return;
                }

                if (!isPaused.getOrDefault(playerUUID, false) &&
                        System.currentTimeMillis() - lastMoveTime.getOrDefault(playerUUID, System.currentTimeMillis()) > 30000) {
                    isPaused.put(playerUUID, true);
                    pauseSystemTime.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(Component.text("AFK detected. Parkour timer paused.", NamedTextColor.GRAY));
                    return;
                }

                if (isPaused.getOrDefault(playerUUID, false)) {
                    return;
                }

                long timePaused = totalPausedTime.get(playerUUID);
                long timeElapsed = (System.currentTimeMillis() - session.startTime() - timePaused) / 1000;
                Score score = objective.getScore(player.getName());
                score.setScore((int) timeElapsed);

            }
        };
        playerTasks.put(player.getUniqueId(), runnable);
        runnable.runTaskTimer(plugin, 0, 20);
    }

    private void stopScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        BukkitRunnable task = playerTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        lastMoveTime.remove(playerUUID);
        isPaused.remove(playerUUID);
        totalPausedTime.remove(playerUUID);
        pauseSystemTime.remove(playerUUID);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (parkourManager.isPlaying(player)) {
            parkourManager.endSession(player, false);
            stopScoreboard(player);
        }
    }
}
