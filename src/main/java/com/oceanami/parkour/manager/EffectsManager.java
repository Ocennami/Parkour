package com.oceanami.parkour.manager;

import com.oceanami.parkour.ParkourPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;

public class EffectsManager {

    private final ParkourPlugin plugin;
    private final FileConfiguration config;
    private final MiniMessage miniMessage;

    public EffectsManager(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void playCheckpointEffect(Player player) {
        if (!config.getBoolean("checkpoint-effect.enabled", true)) return;

        Location loc = player.getLocation();
        try {
            Particle particle = Particle.valueOf(config.getString("checkpoint-effect.particle", "VILLAGER_HAPPY").toUpperCase());
            Sound sound = Sound.valueOf(config.getString("checkpoint-effect.sound", "ENTITY_PLAYER_LEVELUP").toUpperCase());

            player.spawnParticle(particle, loc, 30, 0.5, 0.5, 0.5);
            player.playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle or sound name in config.yml for checkpoint effect.");
        }
    }

    public void playFinishEffect(Player player, String formattedTime) {
        if (!config.getBoolean("finish-effect.enabled", true)) return;

        Location loc = player.getLocation();
        try {
            Particle particle = Particle.valueOf(config.getString("finish-effect.particle", "TOTEM_OF_UNDYING").toUpperCase());
            Sound sound = Sound.valueOf(config.getString("finish-effect.sound", "UI_TOAST_CHALLENGE_COMPLETE").toUpperCase());

            player.spawnParticle(particle, loc, 50, 0.5, 1, 0.5);
            player.playSound(loc, sound, 1.0f, 1.0f);

            if (config.getBoolean("finish-effect.title.enabled", true)) {
                // Using MiniMessage for modern color parsing. Note: Configs should use MiniMessage format e.g., <green><bold>
                String titleStr = config.getString("finish-effect.title.line1", "<green><bold>VICTORY!</bold></green>");
                String subtitleStr = config.getString("finish-effect.title.line2", "<white>Time: <yellow>{time}</yellow></white>").replace("{time}", formattedTime);

                Component titleComponent = miniMessage.deserialize(titleStr);
                Component subtitleComponent = miniMessage.deserialize(subtitleStr);

                long fadeIn = config.getLong("finish-effect.title.fade-in", 10);
                long stay = config.getLong("finish-effect.title.stay", 70);
                long fadeOut = config.getLong("finish-effect.title.fade-out", 20);

                // 1 tick = 50 milliseconds. Convert ticks from config to Duration.
                Title.Times times = Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                );
                Title title = Title.title(titleComponent, subtitleComponent, times);

                player.showTitle(title);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle, sound, or title settings in config.yml for finish effect.");
        }
    }
}
