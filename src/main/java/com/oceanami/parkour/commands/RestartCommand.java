package com.oceanami.parkour.commands;

import com.oceanami.parkour.manager.ParkourManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RestartCommand implements CommandExecutor {

    private final ParkourManager parkourManager;

    public RestartCommand(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (!parkourManager.isPlaying(player)) {
            player.sendMessage(Component.text("You are not currently in a parkour course.").color(NamedTextColor.RED));
            return true;
        }

        parkourManager.restartPlayer(player);
        // The message is now sent from within the restartPlayer method in ParkourManager, so we don't need to send one here.

        return true;
    }
}
