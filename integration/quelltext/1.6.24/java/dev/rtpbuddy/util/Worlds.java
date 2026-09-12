package dev.rtpbuddy.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Small read-only lookups against the client world. Every method tolerates a
 * half-loaded client (null world/player) and returns a sensible fallback.
 */
public final class Worlds {

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    private Worlds() {
    }

    public static String dimensionId(ClientWorld world) {
        return world == null ? OVERWORLD : world.getRegistryKey().getValue().toString();
    }

    /** Biome id at the position, or null when the chunk is not loaded yet. */
    public static String biomeId(ClientWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        try {
            return world.getBiome(pos).getKey()
                    .map(key -> key.getValue().toString())
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Top motion-blocking block at the column, or {@link Integer#MIN_VALUE}. */
    public static int surfaceY(ClientWorld world, int x, int z) {
        if (world == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        } catch (RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    /** Server address, or "" in singleplayer. */
    public static String serverAddress(MinecraftClient client) {
        if (client == null) {
            return "";
        }
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null && entry.address != null) {
            return entry.address;
        }
        return "";
    }

    /** Human-readable dimension label for UI. */
    public static String dimensionLabel(String dimensionId) {
        if (dimensionId == null) {
            return Lang.t("word.unknown");
        }
        return switch (dimensionId) {
            case OVERWORLD -> Lang.t("dim.overworld");
            case NETHER -> Lang.t("dim.nether");
            case END -> Lang.t("dim.end");
            default -> {
                int colon = dimensionId.indexOf(':');
                yield colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
            }
        };
    }

    /** Fallback marker colour when no region colour applies. */
    public static int dimensionColor(String dimensionId) {
        if (dimensionId == null) {
            return 0xFFAAAAAA;
        }
        return switch (dimensionId) {
            case OVERWORLD -> 0xFF5FD35F;
            case NETHER -> 0xFFD1503C;
            case END -> 0xFFB07FD3;
            default -> 0xFF7FA8D3;
        };
    }
}
