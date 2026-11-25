package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Soft particles above Magic Chests, only for players close by. */
public final class ParticleTask implements Runnable {

    private final MagicChestsPlugin plugin;
    private Particle particle = Particle.ENCHANT;
    private int distance = 16;
    private boolean enabled = true;

    public ParticleTask(MagicChestsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("particles.enabled", true);
        distance = Math.max(4, plugin.getConfig().getInt("particles.view-distance", 16));
        String name = plugin.getConfig().getString("particles.type", "ENCHANT").toUpperCase(Locale.ROOT);
        try {
            particle = Particle.valueOf(name);
            if (particle.getDataType() != Void.class) {
                plugin.getLogger().warning("Particle " + name + " needs extra data - using ENCHANT.");
                particle = Particle.ENCHANT;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown particle " + name + " - using ENCHANT.");
            particle = Particle.ENCHANT;
        }
    }

    @Override
    public void run() {
        if (!enabled || plugin.registry().total() == 0) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            for (MagicChest c : plugin.registry().inRange(p.getLocation(), distance, distance)) {
                Location at = new Location(p.getWorld(), c.x() + 0.5, c.y() + 1.05, c.z() + 0.5);
                p.spawnParticle(particle, at, 3, 0.25, 0.1, 0.25, 0.4);
            }
        }
    }
}
