package io.github.wintermarkinteractive.magicchests;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Sneak + right-click a furnace near your Magic Chests: "Smelt from storage". */
public final class FurnaceListener implements Listener {

    private final MagicChestsPlugin plugin;

    public FurnaceListener(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneakOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !e.getPlayer().isSneaking()) return;
        Block b = e.getClickedBlock();
        if (b == null || !FurnaceManager.isStation(b.getType())) return;
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        if (!plugin.getConfig().getBoolean("furnace.enabled", true)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("wintermark.magicchests.use")) return;
        if (plugin.registry().usable(p, b.getLocation()).isEmpty() && plugin.furnaces().get(b) == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        SmeltMenu.open(plugin, p, b.getLocation(), 0, null);
    }

    /** Sneak + right-click a stonecutter or smithing table near your Magic Chests. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneakStation(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !e.getPlayer().isSneaking()) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        StationMenu.Kind kind = StationMenu.kindOf(b.getType());
        if (kind == null) return;
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        if (!plugin.getConfig().getBoolean(kind == StationMenu.Kind.STONECUTTER ? "stonecutter-menu.enabled" : "smithing-menu.enabled", true)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("wintermark.magicchests.use")) return;
        if (plugin.registry().usable(p, b.getLocation()).isEmpty()) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        StationMenu.open(plugin, p, kind, b.getLocation(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (FurnaceManager.isStation(e.getBlock().getType()) && plugin.furnaces().get(e.getBlock()) != null) {
            plugin.furnaces().stop(MagicChest.Key.of(e.getBlock()));
        }
    }
}
