package com.oceanami.parkour.manager;

import java.util.UUID;

/**
 * Simple key representing a block's position in a world.
 */
public record BlockKey(UUID worldId, int x, int y, int z) {
}
