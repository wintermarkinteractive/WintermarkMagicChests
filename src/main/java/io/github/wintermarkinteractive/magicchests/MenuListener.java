package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/** Clicks in the Storage Crafting menu. */
public final class MenuListener implements Listener {

    private final MagicChestsPlugin plugin;

    public MenuListener(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Object h = e.getView().getTopInventory().getHolder(false);
        if (h instanceof CraftMenu || h instanceof SmeltMenu || h instanceof StationMenu || h instanceof StorageMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder(false) instanceof SmeltMenu smelt) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == top) smeltClick(p, smelt, e);
            return;
        }
        if (top.getHolder(false) instanceof StationMenu station) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == top) stationClick(p, station, e);
            return;
        }
        if (top.getHolder(false) instanceof StorageMenu storage) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) storageClick(p, storage, e, e.getClickedInventory() == top);
            return;
        }
        if (!(top.getHolder(false) instanceof CraftMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;
        int slot = e.getRawSlot();
        Location station = menu.station();
        CraftMenu.State state = menu.shownState();

        if (slot < CraftMenu.PER_PAGE) {
            CraftMenu.Entry entry = menu.entry(slot);
            if (entry == null) return;
            if (station.getBlock().getType() != org.bukkit.Material.CRAFTING_TABLE
                    || !station.getWorld().equals(p.getWorld()) || station.distanceSquared(p.getLocation()) > 100) {
                p.closeInventory();
                return;
            }
            List<Block> chests = plugin.registry().usable(p, station);
            int times = e.isShiftClick() ? CraftMenu.shiftCrafts(entry,
                    Math.max(1, plugin.getConfig().getInt("craft-menu.shift-limit", 64))) : 1;
            int done = plugin.filler().craft(p, entry.recipe(), entry.needs(), times, chests);
            if (done <= 0) {
                plugin.messages().send(p, "craft-failed");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            } else {
                int amount = done * Math.max(1, entry.recipe().getResult().getAmount());
                plugin.messages().send(p, "crafted", text("amount", String.valueOf(amount)),
                        text("item", entry.recipe().getResult().getType().getKey().getKey().replace('_', ' ')));
                p.playSound(p.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 0.6f, 1.2f);
            }
            CraftMenu.open(plugin, p, station, state);
            return;
        }
        switch (slot) {
            case CraftMenu.SLOT_PREV -> CraftMenu.open(plugin, p, station, state.page(Math.max(0, state.page() - 1)));
            case CraftMenu.SLOT_NEXT -> CraftMenu.open(plugin, p, station, state.page(state.page() + 1));
            case CraftMenu.SLOT_CATEGORY -> CraftMenu.open(plugin, p, station,
                    state.category(CraftMenu.cycle(state.category(), e.isRightClick())));
            case CraftMenu.SLOT_SEARCH -> {
                if (e.isRightClick()) CraftMenu.open(plugin, p, station, state.search(null));
                else SearchDialog.open(plugin, p, station, state);
            }
            default -> { }
        }
        if (slot == CraftMenu.SLOT_PREV || slot == CraftMenu.SLOT_NEXT || slot == CraftMenu.SLOT_CATEGORY) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1f);
        }
    }

    private void smeltClick(Player p, SmeltMenu menu, InventoryClickEvent e) {
        Location loc = menu.furnace();
        org.bukkit.block.Block b = loc.getBlock();
        if (!FurnaceManager.isStation(b.getType()) || !loc.getWorld().equals(p.getWorld())
                || loc.distanceSquared(p.getLocation()) > 100) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        if (slot < SmeltMenu.PER_PAGE) {
            org.bukkit.Material input = menu.entry(slot);
            if (input == null) return;
            int amount = e.isShiftClick() ? 0
                    : e.isRightClick() ? Math.max(1, plugin.getConfig().getInt("furnace.amounts.right", 64))
                    : Math.max(1, plugin.getConfig().getInt("furnace.amounts.left", 8));
            FurnaceManager.StartResult r = plugin.furnaces().start(p, b, input, amount, menu.output());
            switch (r) {
                case STARTED -> {
                    FurnaceQueue q = plugin.furnaces().get(b);
                    int total = q == null ? amount : q.total();
                    plugin.messages().send(p, "smelt-started", text("amount", String.valueOf(total)),
                            text("item", FurnaceManager.nice(input)));
                    p.playSound(p.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8f, 1f);
                }
                case BUSY -> {
                    org.bukkit.inventory.ItemStack s = b.getState(false) instanceof org.bukkit.block.Furnace f
                            ? f.getInventory().getSmelting() : null;
                    plugin.messages().send(p, "furnace-busy",
                            text("item", s == null ? "?" : FurnaceManager.nice(s.getType())));
                }
                case IN_USE -> plugin.messages().send(p, "furnace-in-use");
                case NONE_IN_CHESTS -> plugin.messages().send(p, "smelt-none");
            }
            SmeltMenu.open(plugin, p, loc, menu.page(), menu.output());
            return;
        }
        switch (slot) {
            case SmeltMenu.SLOT_PREV -> SmeltMenu.open(plugin, p, loc, Math.max(0, menu.page() - 1), menu.output());
            case SmeltMenu.SLOT_NEXT -> SmeltMenu.open(plugin, p, loc, menu.page() + 1, menu.output());
            case SmeltMenu.SLOT_OUTPUT -> {
                boolean out = !menu.output();
                FurnaceQueue q = plugin.furnaces().get(b);
                if (q != null && (q.owner().equals(p.getUniqueId()) || p.hasPermission("wintermark.magicchests.admin"))) {
                    plugin.furnaces().setOutput(q, out);
                }
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1f);
                SmeltMenu.open(plugin, p, loc, menu.page(), out);
            }
            case SmeltMenu.SLOT_STATUS -> {
                FurnaceQueue q = plugin.furnaces().get(b);
                if (q == null) return;
                if (!q.owner().equals(p.getUniqueId()) && !p.hasPermission("wintermark.magicchests.admin")) {
                    plugin.messages().send(p, "furnace-in-use");
                    return;
                }
                plugin.furnaces().stop(q.key());
                plugin.messages().send(p, "smelt-stopped", text("item", FurnaceManager.nice(q.input())));
                SmeltMenu.open(plugin, p, loc, menu.page(), menu.output());
            }
            default -> { }
        }
    }

    private void stationClick(Player p, StationMenu menu, InventoryClickEvent e) {
        Location loc = menu.station();
        if (StationMenu.kindOf(loc.getBlock().getType()) != menu.kind() || !loc.getWorld().equals(p.getWorld())
                || loc.distanceSquared(p.getLocation()) > 100) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        if (slot == StationMenu.SLOT_PREV) {
            StationMenu.open(plugin, p, menu.kind(), loc, Math.max(0, menu.page() - 1));
            return;
        }
        if (slot == StationMenu.SLOT_NEXT) {
            StationMenu.open(plugin, p, menu.kind(), loc, menu.page() + 1);
            return;
        }
        Object entry = menu.entry(slot);
        if (entry == null) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            boolean ok = entry instanceof org.bukkit.Material m ? menu.loadStonecutter(p, m)
                    : entry instanceof org.bukkit.inventory.SmithingTransformRecipe r && menu.loadSmithing(p, r);
            if (!ok) {
                plugin.messages().send(p, "craft-failed");
                StationMenu.open(plugin, p, menu.kind(), loc, menu.page());
            }
        });
    }

    private void storageClick(Player p, StorageMenu menu, InventoryClickEvent e, boolean top) {
        StorageMenu.State state = menu.state();
        if (!top) {
            // Shift-click in your own inventory: put that stack into the Magic Chests.
            if (!e.isShiftClick() || e.getClickedInventory() == null) return;
            if (StorageMenu.deposit(plugin, p, e.getSlot()) > 0) {
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
                StorageMenu.open(plugin, p, state);
            }
            return;
        }
        int slot = e.getRawSlot();
        if (slot < StorageMenu.PER_PAGE) {
            StorageMenu.Stack s = menu.entry(slot);
            if (s == null) return;
            int amount = e.isShiftClick() ? s.amount() : e.isRightClick() ? 1 : s.key().getMaxStackSize();
            int got = StorageMenu.take(plugin, p, s.key(), amount);
            if (got <= 0) plugin.messages().send(p, "inventory-full");
            else p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1f);
            StorageMenu.open(plugin, p, state);
            return;
        }
        switch (slot) {
            case StorageMenu.SLOT_PREV -> StorageMenu.open(plugin, p, state.page(Math.max(0, state.page() - 1)));
            case StorageMenu.SLOT_NEXT -> StorageMenu.open(plugin, p, state.page(state.page() + 1));
            case StorageMenu.SLOT_SORT -> StorageMenu.open(plugin, p, state.sort(!state.byName()));
            case StorageMenu.SLOT_SEARCH -> {
                if (e.isRightClick()) StorageMenu.open(plugin, p, state.search(null));
                else SearchDialog.open(plugin, p, "storage-search-dialog.", state.search(),
                        q -> StorageMenu.open(plugin, p, state.search(q)), () -> StorageMenu.open(plugin, p, state));
            }
            case StorageMenu.SLOT_DEPOSIT -> {
                int moved = 0;
                for (int i = 9; i < 36; i++) moved += StorageMenu.deposit(plugin, p, i);
                plugin.messages().send(p, "deposited", text("amount", String.valueOf(moved)));
                StorageMenu.open(plugin, p, state);
            }
            default -> { }
        }
    }
}
