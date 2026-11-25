package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecutterInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stonecutters loaded from Magic Chests: leftover input goes back into a Magic Chest when the screen closes. */
public final class StationSessions implements Listener {

    private final MagicChestsPlugin plugin;
    private final Map<UUID, Location> open = new HashMap<>();

    public StationSessions(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    void track(Player p, Location station, List<Block> chests) {
        open.put(p.getUniqueId(), station);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Location station = open.remove(e.getPlayer().getUniqueId());
        if (station == null || !(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory() instanceof StonecutterInventory sc)) return;
        ItemStack left = sc.getInputItem();
        if (left == null || left.getType().isAir()) return;
        List<Block> chests = plugin.registry().usable(p, station);
        ItemStack rest = FurnaceManager.store(chests, left.clone());
        sc.setInputItem(rest); // anything that didn't fit goes to the player the vanilla way
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
    }
}
