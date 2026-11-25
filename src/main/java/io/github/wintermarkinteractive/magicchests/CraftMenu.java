package io.github.wintermarkinteractive.magicchests;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/** "Storage Crafting": every recipe you can make from your inventory + Magic Chests, craft 1 or a stack. */
public final class CraftMenu implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_CATEGORY = 46;
    public static final int SLOT_SEARCH = 47;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    /** null = all categories. */
    public record State(int page, CraftingBookCategory category, String search) {
        static State start() {
            return new State(0, null, null);
        }

        State page(int p) {
            return new State(p, category, search);
        }

        State category(CraftingBookCategory c) {
            return new State(0, c, search);
        }

        State search(String s) {
            return new State(0, category, s == null || s.isBlank() ? null : s.trim());
        }
    }

    /** One craftable recipe on a page. */
    record Entry(CraftingRecipe recipe, List<RecipeFiller.Need> needs, int max) {
    }

    /** Shaped/shapeless recipes, built once (cleared on reload). */
    private static List<CraftingRecipe> allRecipes;

    private final MagicChestsPlugin plugin;
    private final Location station;
    private final State state;
    private final List<Entry> shown = new ArrayList<>();
    private Inventory inventory;

    private CraftMenu(MagicChestsPlugin plugin, Location station, State state) {
        this.plugin = plugin;
        this.station = station;
        this.state = state;
    }

    public static void clearCache() {
        allRecipes = null;
    }

    public static void open(MagicChestsPlugin plugin, Player player, Location station, State state) {
        CraftMenu menu = new CraftMenu(plugin, station, state);
        menu.build(player);
        player.openInventory(menu.inventory);
    }

    public Location station() {
        return station;
    }

    public State state() {
        return state;
    }

    public Entry entry(int slot) {
        return slot >= 0 && slot < shown.size() ? shown.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static List<CraftingRecipe> recipes() {
        if (allRecipes != null) return allRecipes;
        List<CraftingRecipe> list = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof CraftingRecipe cr)) continue;
            if (r.getResult().getType().isAir()) continue;
            if (RecipeFiller.needs(r) == null) continue;
            list.add(cr);
        }
        list.sort(Comparator.comparing((CraftingRecipe r) -> r.getResult().getType().getKey().getKey())
                .thenComparing(r -> r.getKey().toString()));
        allRecipes = list;
        return list;
    }

    private void build(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        List<Block> chests = plugin.registry().usable(player, station);
        List<RecipeFiller.Pool> pools = plugin.filler().pools(player, chests, null);

        List<Entry> all = new ArrayList<>();
        String q = state.search() == null ? null : state.search().toLowerCase(Locale.ROOT);
        List<ItemStack> seen = new ArrayList<>();
        for (CraftingRecipe r : recipes()) {
            if (state.category() != null && r.getCategory() != state.category()) continue;
            ItemStack result = r.getResult();
            if (q != null && !searchName(result).contains(q)) continue;
            List<RecipeFiller.Need> needs = RecipeFiller.needs(r);
            if (needs == null) continue;
            int max = plugin.filler().maxCrafts(needs, pools, 999);
            if (max <= 0) continue;
            boolean dup = false;
            for (ItemStack s : seen) {
                if (s.isSimilar(result)) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            seen.add(result);
            all.add(new Entry(r, needs, max));
        }

        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(state.page(), pages - 1));
        TagResolver nav = TagResolver.resolver(text("page", String.valueOf(page + 1)), text("pages", String.valueOf(pages)),
                text("count", String.valueOf(all.size())), text("chests", String.valueOf(chests.size())),
                text("category", categoryName(state.category())),
                text("search", state.search() == null ? "-" : state.search()));
        inventory = Bukkit.createInventory(this, 54, MM.deserialize(cfg.getString("craft-menu.title", "Storage Crafting"), nav));

        int shiftLimit = Math.max(1, cfg.getInt("craft-menu.shift-limit", 64));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) break;
            Entry e = all.get(idx);
            shown.add(e);
            inventory.setItem(i, entryItem(e, shiftLimit));
        }
        if (all.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "craft-menu.empty-name", "craft-menu.empty-lore", nav));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        if (page > 0) inventory.setItem(SLOT_PREV, button(Material.ARROW, "craft-menu.prev-name", null, nav));
        if (page < pages - 1) inventory.setItem(SLOT_NEXT, button(Material.ARROW, "craft-menu.next-name", null, nav));
        inventory.setItem(SLOT_CATEGORY, button(categoryIcon(state.category()), "craft-menu.category-name", "craft-menu.category-lore", nav));
        inventory.setItem(SLOT_SEARCH, button(state.search() == null ? Material.OAK_SIGN : Material.NAME_TAG,
                state.search() == null ? "craft-menu.search-name" : "craft-menu.search-active-name", "craft-menu.search-lore", nav));
        inventory.setItem(SLOT_INFO, button(Material.BOOK, "craft-menu.info-name", "craft-menu.info-lore", nav));
        // Keep the page that was really shown (after clamping).
        if (page != state.page()) shownPage = page;
    }

    private int shownPage = -1;

    public State shownState() {
        return shownPage >= 0 ? state.page(shownPage) : state;
    }

    static int shiftCrafts(Entry e, int shiftLimit) {
        int per = Math.max(1, e.recipe().getResult().getAmount());
        return Math.max(1, Math.min(e.max(), Math.max(1, shiftLimit / per)));
    }

    private ItemStack entryItem(Entry e, int shiftLimit) {
        ItemStack item = e.recipe().getResult().clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int per = Math.max(1, item.getAmount());
        TagResolver tags = TagResolver.resolver(text("max", String.valueOf(e.max())),
                text("one", String.valueOf(per)),
                text("shift", String.valueOf(shiftCrafts(e, shiftLimit) * per)));
        for (String line : plugin.getConfig().getStringList("craft-menu.entry-lore")) {
            lore.add(MagicItems.noItalic(MM.deserialize(line, tags)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        hideStats(item);
        return item;
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
        hideStats(item);
        return item;
    }

    /** Menu items only show our text: no "When in Main Hand" attack damage/speed lines. */
    static void hideStats(ItemStack item) {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());
    }

    private static String searchName(ItemStack item) {
        return item.getType().getKey().getKey().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String categoryName(CraftingBookCategory c) {
        String key = c == null ? "all" : c.name().toLowerCase(Locale.ROOT);
        return plugin.getConfig().getString("craft-menu.categories." + key, key);
    }

    private static Material categoryIcon(CraftingBookCategory c) {
        if (c == null) return Material.CRAFTING_TABLE;
        return switch (c) {
            case BUILDING -> Material.BRICKS;
            case REDSTONE -> Material.REDSTONE;
            case EQUIPMENT -> Material.IRON_SWORD;
            case MISC -> Material.LAVA_BUCKET;
        };
    }

    /** Next (or previous) category: All, Building, Redstone, Equipment, Misc. */
    static CraftingBookCategory cycle(CraftingBookCategory c, boolean back) {
        CraftingBookCategory[] v = CraftingBookCategory.values();
        int i = c == null ? -1 : c.ordinal();
        int n = v.length + 1;
        int next = ((i + 1) + (back ? -1 : 1) + n) % n; // 0 = all
        return next == 0 ? null : v[next - 1];
    }
}
