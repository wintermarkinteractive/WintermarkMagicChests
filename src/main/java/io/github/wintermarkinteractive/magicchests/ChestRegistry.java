package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** All Magic Chests and trust lists, in memory (main thread only). Changes are written to the database. */
public final class ChestRegistry {

    private final MagicChestsPlugin plugin;
    private final Map<MagicChest.Key, MagicChest> byKey = new HashMap<>();
    private final Map<UUID, Map<Long, List<MagicChest>>> byChunk = new HashMap<>();
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();

    public ChestRegistry(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(List<MagicChest> chests, Map<UUID, Set<UUID>> trusted) {
        for (MagicChest c : chests) index(c);
        trust.putAll(trusted);
    }

    private void index(MagicChest c) {
        byKey.put(c.key(), c);
        byChunk.computeIfAbsent(c.world(), w -> new HashMap<>())
                .computeIfAbsent(c.chunkKey(), k -> new ArrayList<>()).add(c);
    }

    public void add(MagicChest c) {
        remove(c.key());
        index(c);
        plugin.database().insertChest(c);
    }

    public MagicChest remove(MagicChest.Key k) {
        MagicChest c = byKey.remove(k);
        if (c == null) return null;
        Map<Long, List<MagicChest>> chunks = byChunk.get(c.world());
        if (chunks != null) {
            List<MagicChest> list = chunks.get(c.chunkKey());
            if (list != null) {
                list.removeIf(o -> o.key().equals(k));
                if (list.isEmpty()) chunks.remove(c.chunkKey());
            }
        }
        plugin.database().deleteChest(k);
        return c;
    }

    public MagicChest get(Block b) {
        return b == null ? null : byKey.get(MagicChest.Key.of(b));
    }

    public int total() {
        return byKey.size();
    }

    public List<MagicChest> ownedBy(UUID owner) {
        List<MagicChest> out = new ArrayList<>();
        for (MagicChest c : byKey.values()) if (c.owner().equals(owner)) out.add(c);
        return out;
    }

    public int count(UUID owner) {
        int n = 0;
        for (MagicChest c : byKey.values()) if (c.owner().equals(owner)) n++;
        return n;
    }

    /** Magic Chests within the box around center (loaded chunks only), nearest first. */
    public List<MagicChest> inRange(Location center, int horizontal, int vertical) {
        List<MagicChest> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        Map<Long, List<MagicChest>> chunks = byChunk.get(center.getWorld().getUID());
        if (chunks == null) return out;
        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        for (int cx = (bx - horizontal) >> 4; cx <= (bx + horizontal) >> 4; cx++) {
            for (int cz = (bz - horizontal) >> 4; cz <= (bz + horizontal) >> 4; cz++) {
                List<MagicChest> list = chunks.get(MagicChest.chunkKey(cx, cz));
                if (list == null) continue;
                for (MagicChest c : list) {
                    if (Math.abs(c.x() - bx) <= horizontal && Math.abs(c.z() - bz) <= horizontal
                            && Math.abs(c.y() - by) <= vertical) out.add(c);
                }
            }
        }
        out.sort(Comparator.comparingDouble(c -> sq(c.x() - bx) + sq(c.y() - by) + sq(c.z() - bz)));
        return out;
    }

    private static double sq(double d) {
        return d * d;
    }

    /**
     * Magic Chests a player may craft with around a station: own chests and chests of players who trust them.
     * Entries whose block is no longer a chest are removed.
     */
    public List<Block> usable(Player p, Location station) {
        return usable(p.getUniqueId(), station);
    }

    /** Same, for a player id (furnaces keep working while their owner is offline). */
    public List<Block> usable(UUID player, Location station) {
        int h = Math.max(1, plugin.getConfig().getInt("range.horizontal", 16));
        int v = Math.max(0, plugin.getConfig().getInt("range.vertical", 8));
        List<Block> out = new ArrayList<>();
        for (MagicChest c : inRange(station, h, v)) {
            if (!canUse(player, c.owner())) continue;
            Block b = c.block();
            if (b == null) continue;
            if (b.getType() != Material.CHEST) {
                remove(c.key());
                continue;
            }
            out.add(b);
        }
        return out;
    }

    /** Every loaded Magic Chest this player may use, anywhere (for /storage with the anywhere permission). */
    public List<Block> usableAnywhere(UUID player) {
        List<Block> out = new ArrayList<>();
        for (MagicChest c : new ArrayList<>(byKey.values())) {
            if (!canUse(player, c.owner())) continue;
            Block b = c.block();
            if (b != null && b.getType() == Material.CHEST) out.add(b);
        }
        return out;
    }

    // ---- trust ----

    public boolean canUse(UUID player, UUID owner) {
        if (player.equals(owner)) return true;
        Set<UUID> set = trust.get(owner);
        return set != null && set.contains(player);
    }

    public boolean trust(UUID owner, UUID other) {
        if (!trust.computeIfAbsent(owner, k -> new HashSet<>()).add(other)) return false;
        plugin.database().addTrust(owner, other);
        return true;
    }

    public boolean untrust(UUID owner, UUID other) {
        Set<UUID> set = trust.get(owner);
        if (set == null || !set.remove(other)) return false;
        plugin.database().removeTrust(owner, other);
        return true;
    }

    public Collection<UUID> trusted(UUID owner) {
        Set<UUID> set = trust.get(owner);
        return set == null ? List.of() : new ArrayList<>(set);
    }
}
