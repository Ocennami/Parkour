package com.oceanami.parkour.manager;

import com.oceanami.parkour.ParkourPlugin;
import com.oceanami.parkour.cache.CourseCache;
import com.oceanami.parkour.database.PlayerTimeDAO;
import com.oceanami.parkour.model.Course;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages active parkour sessions. This manager is not thread-safe and should only be
 * accessed from the Bukkit main thread. Any asynchronous callbacks must schedule their
 * interactions back onto the main thread before invoking its methods.
 */
public class ParkourManager {

    private final ParkourPlugin plugin;
    private final UIManager uiManager;
    private final Map<UUID, ParkourSession> playerSessions;
    private final EffectsManager effectsManager;
    private final PlayerTimeDAO playerTimeDAO;
    private final CourseCache courseCache;
    private final LocationCache locationCache;
    private final boolean teleportOnStart;

    public ParkourManager(ParkourPlugin plugin, UIManager uiManager, CourseCache courseCache, LocationCache locationCache) {
        this.plugin = plugin;
        this.uiManager = uiManager;
        this.courseCache = courseCache;
        this.locationCache = locationCache;
        this.playerSessions = new HashMap<>();
        this.effectsManager = new EffectsManager(plugin);
        this.playerTimeDAO = new PlayerTimeDAO(plugin, courseCache);
        this.teleportOnStart = plugin.getConfig().getBoolean("teleport-on-start", true);
    }

    public void startSession(Player player, String courseName) {
        Optional<Course> courseOpt = courseCache.getCourse(courseName);
        if (courseOpt.isEmpty() || !courseOpt.get().isReady()) {
            player.sendMessage(Component.text("This course is not available to play.", NamedTextColor.RED));
            return;
        }

        // If player is already in a session for the same course, reset them
        if (isPlaying(player) && getSession(player).courseName().equals(courseName)) {
            // If not teleporting, just reset the progress without moving the player
            if (!teleportOnStart) {
                resetPlayerProgress(player);
                player.sendMessage(Component.text("Parkour session restarted at your current location.", NamedTextColor.YELLOW));
                return;
            }
            // If teleporting, proceed with the original reset logic
            resetPlayerToStart(player);
            return;
        }

        Optional<Location> startLocationOpt = locationCache.getStartLocation(courseName);
        if (startLocationOpt.isEmpty()) {
            player.sendMessage(Component.text("The start location for this course is not set. Please contact an admin.", NamedTextColor.RED));
            return;
        }

        Location startLocation = startLocationOpt.get();
        Location initialSessionLocation;

        if (teleportOnStart) {
            // Preserve player's current pitch and yaw
            Location teleportLocation = new Location(
                startLocation.getWorld(),
                startLocation.getX(),
                startLocation.getY(),
                startLocation.getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
            );
            player.teleport(teleportLocation);
            initialSessionLocation = teleportLocation;
        } else {
            // Don't teleport, use player's current location as the starting point for the session
            initialSessionLocation = player.getLocation();
        }

        ParkourSession session = new ParkourSession(player, courseName, System.currentTimeMillis(), 0, initialSessionLocation);
        playerSessions.put(player.getUniqueId(), session);

        uiManager.sendStartMessage(player);
    }

    public void endSession(Player player, boolean completed) {
        endSession(player, completed, 0L);
    }

    public void endSession(Player player, boolean completed, long pausedMillis) {
        ParkourSession session = getSession(player);
        if (session == null) return;

        if (completed) {
            long timeTaken = System.currentTimeMillis() - session.startTime() - pausedMillis;
            String formattedTime = formatTime(timeTaken);

            player.sendMessage(Component.text("You finished the course in ", NamedTextColor.GOLD)
                    .append(Component.text(formattedTime, NamedTextColor.YELLOW))
                    .append(Component.text("!", NamedTextColor.GOLD)));

            effectsManager.playFinishEffect(player, formattedTime);

            playerTimeDAO.savePlayerTime(player, session.courseName(), timeTaken);
        }
        playerSessions.remove(player.getUniqueId());
    }

    public void updateCheckpoint(Player player, int checkpointOrder) {
        ParkourSession currentSession = getSession(player);
        if (currentSession == null) return;

        ParkourSession newSession = currentSession.withLastCheckpoint(checkpointOrder, player.getLocation());
        playerSessions.put(player.getUniqueId(), newSession);
        effectsManager.playCheckpointEffect(player);
    }

    public void restartPlayer(Player player) {
        // Chỉ reset progress, không teleport
        resetPlayerProgress(player);
        player.sendMessage(Component.text("Course restarted. Please go to the start point.").color(NamedTextColor.GREEN));
    }

    public void resetPlayerToStart(Player player) {
        // Chỉ reset progress, không teleport  
        resetPlayerProgress(player);
        player.sendMessage(Component.text("Progress reset. Please return to the start point.").color(NamedTextColor.GREEN));
    }

    private void resetPlayerProgress(Player player) {
        ParkourSession session = getSession(player);
        if (session == null) return;

        // Remove the old session
        playerSessions.remove(player.getUniqueId());

        // Create a new session to reset time and checkpoints
        // The new session should start from the player's current location, as we are not teleporting
        ParkourSession newSession = new ParkourSession(player, session.courseName(), System.currentTimeMillis(), 0, player.getLocation());
        playerSessions.put(player.getUniqueId(), newSession);
    }

    public boolean isPlaying(Player player) {
        return playerSessions.containsKey(player.getUniqueId());
    }

    public ParkourSession getSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    public Collection<ParkourSession> getActiveSessions() {
        return playerSessions.values();
    }

    private String formatTime(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long hundredths = (millis / 10) % 100;
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    public record ParkourSession(Player player, String courseName, long startTime, int lastCheckpoint, Location lastCheckpointLocation) {
        public ParkourSession withLastCheckpoint(int newCheckpoint, Location newLocation) {
            return new ParkourSession(player, courseName, startTime, newCheckpoint, newLocation);
        }
    }
}
