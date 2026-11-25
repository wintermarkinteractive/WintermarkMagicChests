package io.github.wintermarkinteractive.magicchests;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Crafting tables near Magic Chests: the recipe book fills the grid from the chests. */
public final class CraftingListener implements Listener {

    private final MagicChestsPlugin plugin;
    /** Crafting table each player opened (cleared when the screen closes). */
    private final Map<UUID, Location> stations = new HashMap<>();
    /** Players who already got the recipe-book tip this session. */
    private final Set<UUID> tipped = new HashSet<>();

    public CraftingListener(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sneak + right-click a crafting table near your Magic Chests: open Storage Crafting instead. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneakOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !e.getPlayer().isSneaking()) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CRAFTING_TABLE) return;
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        if (!plugin.getConfig().getBoolean("craft-menu.enabled", true)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("wintermark.magicchests.use")) return;
        if (plugin.registry().usable(p, b.getLocation()).isEmpty()) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        CraftMenu.open(plugin, p, b.getLocation(), CraftMenu.State.start());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CRAFTING_TABLE) return;
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        stations.put(e.getPlayer().getUniqueId(), b.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getType() != InventoryType.WORKBENCH) return;
        Location station = station(p);
        if (station == null || tipped.contains(p.getUniqueId())) return;
        if (!p.hasPermission("wintermark.magicchests.use")) return;
        if (plugin.registry().usable(p, station).isEmpty()) return;
        tipped.add(p.getUniqueId());
        plugin.messages().send(p, "recipe-tip");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getType() == InventoryType.WORKBENCH) stations.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stations.remove(e.getPlayer().getUniqueId());
        tipped.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRecipeClick(PlayerRecipeBookClickEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("wintermark.magicchests.use")) return;
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getType() != InventoryType.WORKBENCH || !(top instanceof CraftingInventory grid)) return;
        Location station = station(p);
        if (station == null) return;
        List<Block> chests = plugin.registry().usable(p, station);
        if (chests.isEmpty()) return; // no Magic Chests: plain vanilla
        Recipe recipe = Bukkit.getRecipe(e.getRecipe());
        if (recipe == null) return;
        if (plugin.filler().fill(p, grid, recipe, e.isMakeAll(), chests)) {
            e.setCancelled(true);
            plugin.messages().send(p, "filled-from-chests");
            Bukkit.getScheduler().runTask(plugin, p::updateInventory);
        }
    }

    /** The crafting table this player has open, if it is still close by. */
    private Location station(Player p) {
        Location loc = stations.get(p.getUniqueId());
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(p.getWorld())) return null;
        if (loc.distanceSquared(p.getLocation()) > 100) return null;
        return loc;
    }
}
