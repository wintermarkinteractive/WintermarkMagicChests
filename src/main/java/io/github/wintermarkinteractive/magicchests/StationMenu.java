package io.github.wintermarkinteractive.magicchests;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.github.wintermarkinteractive.magicchests.Messages.component;
import static io.github.wintermarkinteractive.magicchests.Messages.text;

/**
 * Helpers for the stonecutter and the smithing table: pick something, the plugin loads the station's
 * input slots from your Magic Chests (and inventory) and opens the normal station screen.
 */
public final class StationMenu implements InventoryHolder {

    public enum Kind { STONECUTTER, SMITHING }

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_NEXT = 53;

    private static List<StonecuttingRecipe> cutRecipes;
    private static List<SmithingTransformRecipe> smithRecipes;

    private final MagicChestsPlugin plugin;
    private final Kind kind;
    private final Location station;
    private final int page;
    private final List<Object> shown = new ArrayList<>();
    private Inventory inventory;

    private StationMenu(MagicChestsPlugin plugin, Kind kind, Location station, int page) {
        this.plugin = plugin;
        this.kind = kind;
        this.station = station;
        this.page = page;
    }

    public static void clearCache() {
        cutRecipes = null;
        smithRecipes = null;
    }

    public static Kind kindOf(Material m) {
        if (m == Material.STONECUTTER) return Kind.STONECUTTER;
        if (m == Material.SMITHING_TABLE) return Kind.SMITHING;
        return null;
    }

    public static void open(MagicChestsPlugin plugin, Player p, Kind kind, Location station, int page) {
        StationMenu menu = new StationMenu(plugin, kind, station, page);
        menu.build(p);
        p.openInventory(menu.inventory);
    }

    public Kind kind() { return kind; }
    public Location station() { return station; }
    public int page() { return page; }

    public Object entry(int slot) {
        return slot >= 0 && slot < shown.size() ? shown.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static List<StonecuttingRecipe> cutRecipes() {
        if (cutRecipes == null) {
            List<StonecuttingRecipe> list = new ArrayList<>();
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof StonecuttingRecipe r) list.add(r);
            }
            cutRecipes = list;
        }
        return cutRecipes;
    }

    private static List<SmithingTransformRecipe> smithRecipes() {
        if (smithRecipes == null) {
            List<SmithingTransformRecipe> list = new ArrayList<>();
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof SmithingTransformRecipe r) list.add(r);
            }
            smithRecipes = list;
        }
        return smithRecipes;
    }

    private void build(Player p) {
        List<Block> chests = plugin.registry().usable(p, station);
        List<Object> all = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        String prefix = kind == Kind.STONECUTTER ? "stonecutter-menu." : "smithing-menu.";

        if (kind == Kind.STONECUTTER) {
            Map<Material, Integer> contents = FurnaceManager.contents(chests);
            for (Map.Entry<Material, Integer> e : contents.entrySet()) {
                ItemStack in = new ItemStack(e.getKey());
                int options = 0;
                for (StonecuttingRecipe r : cutRecipes()) if (r.getInputChoice().test(in)) options++;
                if (options == 0) continue;
                all.add(e.getKey());
                TagResolver t = TagResolver.resolver(text("have", String.valueOf(e.getValue())),
                        text("options", String.valueOf(options)),
                        text("load", String.valueOf(Math.min(e.getValue(), e.getKey().getMaxStackSize()))));
                icons.add(MenuItems.withLore(plugin, in, prefix + "entry-lore", t));
            }
        } else {
            for (SmithingTransformRecipe r : smithRecipes()) {
                Found template = find(p, chests, r.getTemplate(), false);
                Found base = template == null ? null : find(p, chests, r.getBase(), true);
                Found addition = base == null ? null : find(p, chests, r.getAddition(), false);
                if (template == null || base == null || addition == null) continue;
                all.add(r);
                TagResolver t = TagResolver.resolver(component("template", template.item().effectiveName()),
                        component("base", base.item().effectiveName()),
                        component("addition", addition.item().effectiveName()),
                        text("from", base.chest() ? plugin.getConfig().getString(prefix + "from-chest", "Magic Chest")
                                : plugin.getConfig().getString(prefix + "from-inventory", "your inventory")));
                icons.add(MenuItems.withLore(plugin, r.getResult(), prefix + "entry-lore", t));
            }
        }

        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        TagResolver nav = TagResolver.resolver(text("page", String.valueOf(pg + 1)), text("pages", String.valueOf(pages)));
        inventory = Bukkit.createInventory(this, 54, MenuItems.title(plugin, prefix + "title", kind.name(), nav));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= all.size()) break;
            shown.add(all.get(idx));
            inventory.setItem(i, icons.get(idx));
        }
        if (all.isEmpty()) {
            inventory.setItem(22, MenuItems.button(plugin, Material.BARRIER, prefix + "empty-name", prefix + "empty-lore", nav));
        }
        ItemStack filler = MenuItems.filler();
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        if (pg > 0) inventory.setItem(SLOT_PREV, MenuItems.button(plugin, Material.ARROW, "craft-menu.prev-name", null, nav));
        if (pg < pages - 1) inventory.setItem(SLOT_NEXT, MenuItems.button(plugin, Material.ARROW, "craft-menu.next-name", null, nav));
        inventory.setItem(49, MenuItems.button(plugin, Material.BOOK, prefix + "info-name", prefix + "info-lore", nav));
    }

    /** Where one matching item is: a slot in the inventory or in a Magic Chest. */
    record Found(Inventory inv, int slot, ItemStack item, boolean chest) {
    }

    /**
     * Finds one item for a choice. preferInventory: the player's own gear (enchanted tools...) is used first;
     * otherwise Magic Chests first. Other chest items must be plain when only-plain-items is on.
     */
    Found find(Player p, List<Block> chests, RecipeChoice choice, boolean preferInventory) {
        if (choice == null) return null;
        Found inv = null;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it != null && !it.getType().isAir() && !plugin.items().isProtected(it) && choice.test(it)) {
                inv = new Found(p.getInventory(), slot, it, false);
                break;
            }
        }
        if (preferInventory && inv != null) return inv;
        boolean plainOnly = plugin.getConfig().getBoolean("only-plain-items", true);
        for (Block b : chests) {
            if (!(b.getState(false) instanceof Chest chest)) continue;
            Inventory ci = chest.getBlockInventory();
            for (int slot = 0; slot < ci.getSize(); slot++) {
                ItemStack it = ci.getItem(slot);
                if (it == null || it.getType().isAir() || plugin.items().isProtected(it)) continue;
                // The item being upgraded may be enchanted or renamed (that is the point of smithing);
                // templates and materials must be plain like every other ingredient.
                if (plainOnly && !preferInventory && !MagicItems.isPlain(it)) continue;
                if (choice.test(it)) return new Found(ci, slot, it, true);
            }
        }
        return inv;
    }

    /** Takes one item from where it was found (checked again first). */
    static ItemStack takeOne(Found f) {
        ItemStack now = f.inv().getItem(f.slot());
        if (now == null || !now.isSimilar(f.item())) return null;
        ItemStack one = now.asOne();
        if (now.getAmount() <= 1) f.inv().setItem(f.slot(), null);
        else {
            now.setAmount(now.getAmount() - 1);
            f.inv().setItem(f.slot(), now);
        }
        return one;
    }

    /** Stonecutter: loads up to a stack of the material and opens the stonecutter. */
    boolean loadStonecutter(Player p, Material input) {
        List<Block> chests = plugin.registry().usable(p, station);
        int got = FurnaceManager.take(chests, input, input.getMaxStackSize());
        if (got <= 0) return false;
        InventoryView view = org.bukkit.inventory.MenuType.STONECUTTER.builder().location(station).checkReachable(true).build(p);
        p.openInventory(view);
        if (view != null && view.getTopInventory() instanceof StonecutterInventory sc) {
            sc.setInputItem(new ItemStack(input, got));
            plugin.stations().track(p, station, chests);
            return true;
        }
        ItemStack rest = FurnaceManager.store(chests, new ItemStack(input, got));
        if (rest != null) plugin.filler().give(p, rest, chests);
        return false;
    }

    /** Smithing table: puts template, base and addition in and opens the smithing table. */
    boolean loadSmithing(Player p, SmithingTransformRecipe r) {
        List<Block> chests = plugin.registry().usable(p, station);
        Found template = find(p, chests, r.getTemplate(), false);
        Found base = find(p, chests, r.getBase(), true);
        Found addition = find(p, chests, r.getAddition(), false);
        if (template == null || base == null || addition == null) return false;
        ItemStack t = takeOne(template), b = takeOne(base), a = takeOne(addition);
        if (t == null || b == null || a == null) {
            for (ItemStack back : new ItemStack[]{t, b, a}) if (back != null) plugin.filler().give(p, back, chests);
            return false;
        }
        InventoryView view = org.bukkit.inventory.MenuType.SMITHING.builder().location(station).checkReachable(true).build(p);
        p.openInventory(view);
        if (view != null && view.getTopInventory() instanceof SmithingInventory sm) {
            sm.setInputTemplate(t);
            sm.setInputEquipment(b);
            sm.setInputMineral(a);
            return true;
        }
        for (ItemStack back : new ItemStack[]{t, b, a}) plugin.filler().give(p, back, chests);
        return false;
    }
}
