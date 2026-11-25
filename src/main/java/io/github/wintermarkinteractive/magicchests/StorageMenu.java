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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/** /storage: everything in your Magic Chests in one menu. Take items out, put items in. */
public final class StorageMenu implements InventoryHolder {

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_SORT = 47;
    public static final int SLOT_SEARCH = 48;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_DEPOSIT = 51;
    public static final int SLOT_NEXT = 53;

    public record State(int page, boolean byName, String search) {
        static State start() {
            return new State(0, false, null);
        }

        State page(int p) { return new State(p, byName, search); }
        State sort(boolean name) { return new State(0, name, search); }
        State search(String s) { return new State(0, byName, s == null || s.isBlank() ? null : s.trim()); }
    }

    /** One kind of item and how many of it are stored. */
    record Stack(ItemStack key, int amount) {
    }

    private final MagicChestsPlugin plugin;
    private final State state;
    private final List<Stack> shown = new ArrayList<>();
    private Inventory inventory;
    private int shownPage;

    private StorageMenu(MagicChestsPlugin plugin, State state) {
        this.plugin = plugin;
        this.state = state;
    }

    public static void open(MagicChestsPlugin plugin, Player p, State state) {
        StorageMenu menu = new StorageMenu(plugin, state);
        menu.build(p);
        p.openInventory(menu.inventory);
    }

    public State state() {
        return state.page(shownPage);
    }

    public Stack entry(int slot) {
        return slot >= 0 && slot < shown.size() ? shown.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** The Magic Chests /storage may use for this player (near them, or everywhere with the permission). */
    static List<Block> chests(MagicChestsPlugin plugin, Player p) {
        if (p.hasPermission("wintermark.magicchests.storage.anywhere")) {
            return plugin.registry().usableAnywhere(p.getUniqueId());
        }
        int r = Math.max(1, plugin.getConfig().getInt("storage-command.range", 32));
        Location at = p.getLocation();
        List<Block> out = new ArrayList<>();
        for (MagicChest c : plugin.registry().inRange(at, r, r)) {
            if (!plugin.registry().canUse(p.getUniqueId(), c.owner())) continue;
            Block b = c.block();
            if (b != null && b.getType() == Material.CHEST) out.add(b);
        }
        return out;
    }

    static List<Stack> contents(List<Block> chests) {
        List<Stack> list = new ArrayList<>();
        for (Block b : chests) {
            if (!(b.getState(false) instanceof Chest chest)) continue;
            for (ItemStack it : chest.getBlockInventory().getContents()) {
                if (it == null || it.getType().isAir()) continue;
                boolean merged = false;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).key().isSimilar(it)) {
                        list.set(i, new Stack(list.get(i).key(), list.get(i).amount() + it.getAmount()));
                        merged = true;
                        break;
                    }
                }
                if (!merged) list.add(new Stack(it.asOne(), it.getAmount()));
            }
        }
        return list;
    }

    private void build(Player p) {
        List<Block> chests = chests(plugin, p);
        List<Stack> all = contents(chests);
        if (state.search() != null) {
            String q = state.search().toLowerCase(Locale.ROOT);
            all.removeIf(s -> !s.key().getType().getKey().getKey().replace('_', ' ').contains(q));
        }
        if (state.byName()) all.sort(Comparator.comparing(s -> s.key().getType().getKey().getKey()));
        else all.sort(Comparator.comparingInt(Stack::amount).reversed());

        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        shownPage = Math.max(0, Math.min(state.page(), pages - 1));
        int total = 0;
        for (Stack s : all) total += s.amount();
        TagResolver nav = TagResolver.resolver(text("page", String.valueOf(shownPage + 1)), text("pages", String.valueOf(pages)),
                text("chests", String.valueOf(chests.size())), text("kinds", String.valueOf(all.size())),
                text("total", String.valueOf(total)), text("search", state.search() == null ? "-" : state.search()),
                text("sort", plugin.getConfig().getString(state.byName() ? "storage-menu.sort-name-text" : "storage-menu.sort-amount-text",
                        state.byName() ? "name" : "amount")));
        inventory = Bukkit.createInventory(this, 54, MenuItems.title(plugin, "storage-menu.title", "Storage", nav));

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = shownPage * PER_PAGE + i;
            if (idx >= all.size()) break;
            Stack s = all.get(idx);
            shown.add(s);
            ItemStack icon = MenuItems.withLore(plugin, s.key(), "storage-menu.entry-lore",
                    TagResolver.resolver(text("amount", String.valueOf(s.amount())),
                            text("stack", String.valueOf(Math.min(s.amount(), s.key().getMaxStackSize())))));
            icon.setAmount(Math.max(1, Math.min(s.amount(), icon.getMaxStackSize())));
            inventory.setItem(i, icon);
        }
        if (all.isEmpty()) {
            inventory.setItem(22, MenuItems.button(plugin, Material.BARRIER, "storage-menu.empty-name", "storage-menu.empty-lore", nav));
        }
        ItemStack filler = MenuItems.filler();
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        if (shownPage > 0) inventory.setItem(SLOT_PREV, MenuItems.button(plugin, Material.ARROW, "craft-menu.prev-name", null, nav));
        if (shownPage < pages - 1) inventory.setItem(SLOT_NEXT, MenuItems.button(plugin, Material.ARROW, "craft-menu.next-name", null, nav));
        inventory.setItem(SLOT_SORT, MenuItems.button(plugin, Material.HOPPER, "storage-menu.sort-name", "storage-menu.sort-lore", nav));
        inventory.setItem(SLOT_SEARCH, MenuItems.button(plugin, state.search() == null ? Material.OAK_SIGN : Material.NAME_TAG,
                state.search() == null ? "craft-menu.search-name" : "craft-menu.search-active-name", "craft-menu.search-lore", nav));
        inventory.setItem(SLOT_INFO, MenuItems.button(plugin, Material.BOOK, "storage-menu.info-name", "storage-menu.info-lore", nav));
        inventory.setItem(SLOT_DEPOSIT, MenuItems.button(plugin, Material.CHEST_MINECART, "storage-menu.deposit-name", "storage-menu.deposit-lore", nav));
    }

    /** Takes up to `amount` of this kind out of the chests into the player's inventory (only what fits). */
    static int take(MagicChestsPlugin plugin, Player p, ItemStack key, int amount) {
        int space = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it == null || it.getType().isAir()) space += key.getMaxStackSize();
            else if (it.isSimilar(key)) space += Math.max(0, it.getMaxStackSize() - it.getAmount());
        }
        int want = Math.min(amount, space);
        if (want <= 0) return 0;
        int left = want;
        for (Block b : chests(plugin, p)) {
            if (left <= 0) break;
            if (!(b.getState(false) instanceof Chest chest)) continue;
            Inventory inv = chest.getBlockInventory();
            for (int slot = 0; slot < inv.getSize() && left > 0; slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null || !it.isSimilar(key)) continue;
                int use = Math.min(left, it.getAmount());
                if (use >= it.getAmount()) inv.setItem(slot, null);
                else {
                    it.setAmount(it.getAmount() - use);
                    inv.setItem(slot, it);
                }
                left -= use;
            }
        }
        int got = want - left;
        int rest = got;
        while (rest > 0) {
            int n = Math.min(rest, key.getMaxStackSize());
            for (ItemStack over : p.getInventory().addItem(key.asQuantity(n)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), over);
            }
            rest -= n;
        }
        return got;
    }

    /** Puts one inventory slot into the chests; returns how many items went in. */
    static int deposit(MagicChestsPlugin plugin, Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        if (it == null || it.getType().isAir() || plugin.items().isMagic(it)) return 0;
        ItemStack rest = FurnaceManager.store(chests(plugin, p), it.clone());
        int moved = it.getAmount() - (rest == null ? 0 : rest.getAmount());
        p.getInventory().setItem(slot, rest);
        return moved;
    }
}
