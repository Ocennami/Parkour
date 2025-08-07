package com.oceanami.parkour.scheduler;

import com.oceanami.parkour.ParkourPlugin;
import com.oceanami.parkour.manager.ParkourManager;
import com.oceanami.parkour.manager.UIManager;
import org.bukkit.scheduler.BukkitRunnable;

public class ParkourTask extends BukkitRunnable {

    private final ParkourManager parkourManager;
    private final UIManager uiManager;

    public ParkourTask(ParkourPlugin plugin) {
        this.parkourManager = plugin.getParkourManager();
        this.uiManager = plugin.getUiManager();
    }

    @Override
    public void run() {
        // This task runs for all players in a session
        parkourManager.getActiveSessions().forEach(session -> {
            if (session.player().isOnline()) {
                long timeElapsed = System.currentTimeMillis() - session.startTime();
                uiManager.updateTime(session.player(), timeElapsed);
            }
        });
    }
}
