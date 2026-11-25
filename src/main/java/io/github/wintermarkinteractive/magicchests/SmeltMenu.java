package io.github.wintermarkinteractive.magicchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.wintermarkinteractive.magicchests.Messages.component;
import static io.github.wintermarkinteractive.magicchests.Messages.text;

/** "Smelt from storage": pick what this furnace should cook from your Magic Chests. */
public final class SmeltMenu implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_OUTPUT = 47;
    public static final int SLOT_STATUS = 49;
    public static final int SLOT_NEXT = 53;

    private final MagicChestsPlugin plugin;
    private final Location furnace;
    private final int page;
    private final boolean output;
    private final List<Material> shown = new ArrayList<>();
    private Inventory inventory;

    private SmeltMenu(MagicChestsPlugin plugin, Location furnace, int page, boolean output) {
        this.plugin = plugin;
        this.furnace = furnace;
        this.page = page;
        this.output = output;
    }

    public static void open(MagicChestsPlugin plugin, Player p, Location furnace, int page, Boolean output) {
        boolean out;
        if (output != null) out = output;
        else {
            FurnaceQueue q = plugin.furnaces().get(furnace.getBlock());
            out = q != null ? q.output() : plugin.getConfig().getBoolean("furnace.output-to-chest", true);
        }
        SmeltMenu menu = new SmeltMenu(plugin, furnace, page, out);
        menu.build(p);
        p.openInventory(menu.inventory);
    }

    public Location furnace() { return furnace; }
    public int page() { return page; }
    public boolean output() { return output; }

    public Material entry(int slot) {
        return slot >= 0 && slot < shown.size() ? shown.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        Block b = furnace.getBlock();
        Material station = b.getType();
        List<Block> chests = plugin.registry().usable(p, furnace);
        Map<Material, Integer> contents = FurnaceManager.contents(chests);

        List<Material> all = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : contents.entrySet()) {
            ItemStack res = plugin.furnaces().resultOf(station, e.getKey());
            if (res == null) continue;
            all.add(e.getKey());
            results.add(res);
        }

        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String stationName = cfg.getString("smelt-menu.stations." + station.name().toLowerCase(java.util.Locale.ROOT),
                FurnaceManager.nice(station));
        TagResolver nav = TagResolver.resolver(text("page", String.valueOf(pg + 1)), text("pages", String.valueOf(pages)),
                text("station", stationName), text("chests", String.valueOf(chests.size())));
        inventory = Bukkit.createInventory(this, 54, MM.deserialize(cfg.getString("smelt-menu.title", "Smelt from storage"), nav));

        int left = Math.max(1, cfg.getInt("furnace.amounts.left", 8));
        int right = Math.max(1, cfg.getInt("furnace.amounts.right", 64));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= all.size()) break;
            Material m = all.get(idx);
            shown.add(m);
            int have = contents.get(m);
            ItemStack icon = new ItemStack(m);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            TagResolver tags = TagResolver.resolver(text("have", String.valueOf(have)),
                    text("left", String.valueOf(Math.min(left, have))), text("right", String.valueOf(Math.min(right, have))),
                    component("result", results.get(idx).effectiveName()));
            for (String line : cfg.getStringList("smelt-menu.entry-lore")) lore.add(MagicItems.noItalic(MM.deserialize(line, tags)));
            meta.lore(lore);
            icon.setItemMeta(meta);
            CraftMenu.hideStats(icon);
            inventory.setItem(i, icon);
        }
        if (all.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "smelt-menu.empty-name", "smelt-menu.empty-lore", nav));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        if (pg > 0) inventory.setItem(SLOT_PREV, button(Material.ARROW, "smelt-menu.prev-name", null, nav));
        if (pg < pages - 1) inventory.setItem(SLOT_NEXT, button(Material.ARROW, "smelt-menu.next-name", null, nav));
        inventory.setItem(SLOT_OUTPUT, output
                ? button(Material.HOPPER, "smelt-menu.output-on-name", "smelt-menu.output-lore", nav)
                : button(Material.FURNACE_MINECART, "smelt-menu.output-off-name", "smelt-menu.output-lore", nav));

        FurnaceQueue q = plugin.furnaces().get(b);
        if (q == null) {
            inventory.setItem(SLOT_STATUS, button(Material.CLOCK, "smelt-menu.status-none-name", "smelt-menu.status-none-lore", nav));
        } else {
            TagResolver st = TagResolver.resolver(nav, text("item", FurnaceManager.nice(q.input())),
                    text("remaining", String.valueOf(q.remaining())), text("total", String.valueOf(q.total())),
                    text("owner", nameOf(q)));
            inventory.setItem(SLOT_STATUS, button(q.input(), "smelt-menu.status-name", "smelt-menu.status-lore", st));
        }
    }

    private static String nameOf(FurnaceQueue q) {
        String n = Bukkit.getOfflinePlayer(q.owner()).getName();
        return n == null ? "?" : n;
    }

    private ItemStack button(Material mat, String namePath, String lorePath, TagResolver tags) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MagicItems.noItalic(MM.deserialize(plugin.getConfig().getString(namePath, ""), tags)));
        if (lorePath != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.getConfig().getStringList(lorePath)) lore.add(MagicItems.noItalic(MM.deserialize(line, tags)));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        CraftMenu.hideStats(item);
        return item;
    }
}
