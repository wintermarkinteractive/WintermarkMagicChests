package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/** One placed Magic Chest block (each half of a double chest is its own entry). */
public record MagicChest(UUID world, int x, int y, int z, UUID owner, long placed) {

    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    public Key key() {
        return new Key(world, x, y, z);
    }

    public long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }

    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    /** The block, or null when the world isn't loaded. Does not load chunks. */
    public Block block() {
        World w = Bukkit.getWorld(world);
        if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }

    public Location center(World w) {
        return new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }
}
