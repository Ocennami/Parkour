package com.oceanami.parkour.manager;

import com.oceanami.parkour.ParkourPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class UIManager {

    private final ParkourPlugin plugin;

    public UIManager(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendStartMessage(Player player) {
        if (plugin.getConfig().getBoolean("start-message.enabled", true)) {
            String message = plugin.getConfig().getString("start-message.text", "&eTime started!");
            int duration = plugin.getConfig().getInt("start-message.duration", 3);

            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
            player.sendActionBar(component);

            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendActionBar(Component.text("")); // Clear the action bar
                }
            }.runTaskLater(plugin, duration * 20L); // duration in seconds
        }
    }
}
