package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills a 3x3 crafting grid for a recipe with items from the player's inventory and Magic Chests.
 * Items are always removed from their source before they are put into the grid.
 */
public final class RecipeFiller {

    /** One grid slot (0-8) that needs an ingredient. */
    record Need(int slot, RecipeChoice choice) {
    }

    /** One item slot we may take from. */
    private record Source(Inventory inv, int slot, boolean chest) {
    }

    /** A kind of item (amount 1) and every slot holding it. */
    static final class Pool {
        final ItemStack key;
        final List<Source> chestSources = new ArrayList<>();
        final List<Source> invSources = new ArrayList<>();
        int amount;

        Pool(ItemStack key) {
            this.key = key;
        }
    }

    private final MagicChestsPlugin plugin;

    public RecipeFiller(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    /** The grid slots and choices of a crafting recipe, or null for recipe types we leave to vanilla. */
    static List<Need> needs(Recipe recipe) {
        List<Need> needs = new ArrayList<>();
        if (recipe instanceof ShapedRecipe sr) {
            String[] shape = sr.getShape();
            if (shape.length > 3) return null;
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (int r = 0; r < shape.length; r++) {
                if (shape[r].length() > 3) return null;
                for (int c = 0; c < shape[r].length(); c++) {
                    RecipeChoice choice = map.get(shape[r].charAt(c));
                    if (choice != null) needs.add(new Need(r * 3 + c, choice));
                }
            }
        } else if (recipe instanceof ShapelessRecipe sl) {
            List<RecipeChoice> list = sl.getChoiceList();
            if (list.size() > 9) return null;
            for (int i = 0; i < list.size(); i++) needs.add(new Need(i, list.get(i)));
        } else {
            return null;
        }
        return needs.isEmpty() ? null : needs;
    }

    /**
     * Tries to fill the grid. Returns false (and changes nothing) when the recipe can't be made
     * from inventory + grid + chests, so vanilla can show its ghost recipe.
     */
    public boolean fill(Player player, CraftingInventory grid, Recipe recipe, boolean makeAll, List<Block> chests) {
        List<Need> needs = needs(recipe);
        if (needs == null) return false;

        // Clicking the same recipe again adds one more set (like vanilla); shift fills up to a stack.
        int current = currentSets(needs, grid);
        int target = makeAll ? 64 : current + 1;

        // Dry run: the grid's current items count as inventory (they go back to the inventory first).
        List<Pool> dry = pools(player, chests, grid);
        int sets = bestSets(needs, dry, target);
        if (sets <= 0) return false;
        if (sets <= current) return true; // can't add more: leave the grid as it is

        // Real run: grid back to the player, then take the ingredients.
        returnGrid(player, grid, chests);
        List<Pool> pools = pools(player, chests, null);
        int[] alloc = allocate(needs, pools, sets, true);
        while (alloc == null && sets > 1) {
            sets--;
            alloc = allocate(needs, pools, sets, true);
        }
        if (alloc == null) return true; // grid was emptied; nothing left to place (rare: inventory overflow dropped items)

        boolean chestsFirst = chestsFirst();
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < needs.size(); i++) {
            Pool pool = pools.get(alloc[i]);
            ItemStack taken = take(pool, sets, chestsFirst);
            if (taken != null) matrix[needs.get(i).slot()] = taken;
        }
        grid.setMatrix(matrix);
        return true;
    }

    /** Most sets (up to max) that fit in the grid; 0 = none. */
    private int bestSets(List<Need> needs, List<Pool> pools, int max) {
        for (int s = Math.min(64, max); s >= 1; s--) {
            if (allocate(needs, pools, s, true) != null) return s;
        }
        return 0;
    }

    /** How many sets of this recipe the grid holds right now in our layout (0 = something else / empty). */
    private static int currentSets(List<Need> needs, CraftingInventory grid) {
        ItemStack[] m = grid.getMatrix();
        boolean[] used = new boolean[m.length];
        int min = Integer.MAX_VALUE;
        for (Need n : needs) {
            if (n.slot() >= m.length) return 0;
            ItemStack it = m[n.slot()];
            if (it == null || it.getType().isAir() || !n.choice().test(it)) return 0;
            used[n.slot()] = true;
            min = Math.min(min, it.getAmount());
        }
        for (int i = 0; i < m.length; i++) {
            if (!used[i] && m[i] != null && !m[i].getType().isAir()) return 0;
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * For every need the index of a pool that can give `sets` items.
     * stackLimit: each grid slot holds one stack (recipe book); false for menu crafting.
     */
    int[] allocate(List<Need> needs, List<Pool> pools, int sets, boolean stackLimit) {
        int[] remaining = new int[pools.size()];
        for (int i = 0; i < pools.size(); i++) remaining[i] = pools.get(i).amount;
        int[] alloc = new int[needs.size()];
        for (int n = 0; n < needs.size(); n++) {
            RecipeChoice choice = needs.get(n).choice();
            int best = -1;
            for (int i = 0; i < pools.size(); i++) {
                Pool p = pools.get(i);
                if (remaining[i] < sets || (stackLimit && p.key.getMaxStackSize() < sets)) continue;
                if (!choice.test(p.key)) continue;
                if (best < 0 || remaining[i] > remaining[best]) best = i;
            }
            if (best < 0) return null;
            remaining[best] -= sets;
            alloc[n] = best;
        }
        return alloc;
    }

    /** Groups every usable item from the inventory (and grid), and from the Magic Chests. */
    List<Pool> pools(Player player, List<Block> chests, CraftingInventory gridAsInventory) {
        List<Pool> pools = new ArrayList<>();
        Map<Material, List<Pool>> byType = new HashMap<>();
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            add(pools, byType, inv.getItem(slot), new Source(inv, slot, false), false);
        }
        if (gridAsInventory != null) {
            for (ItemStack it : gridAsInventory.getMatrix()) add(pools, byType, it, null, false);
        }
        boolean plainOnly = plugin.getConfig().getBoolean("only-plain-items", true);
        for (Block b : chests) {
            if (!(b.getState(false) instanceof Chest chest)) continue;
            Inventory ci = chest.getBlockInventory();
            for (int slot = 0; slot < ci.getSize(); slot++) {
                ItemStack it = ci.getItem(slot);
                if (plainOnly && it != null && !MagicItems.isPlain(it)) continue;
                add(pools, byType, it, new Source(ci, slot, true), true);
            }
        }
        return pools;
    }

    private void add(List<Pool> pools, Map<Material, List<Pool>> byType, ItemStack it, Source src, boolean chest) {
        if (it == null || it.getType().isAir() || plugin.items().isProtected(it)) return;
        List<Pool> same = byType.computeIfAbsent(it.getType(), m -> new ArrayList<>());
        Pool pool = null;
        for (Pool p : same) {
            if (p.key.isSimilar(it)) {
                pool = p;
                break;
            }
        }
        if (pool == null) {
            pool = new Pool(it.asOne());
            same.add(pool);
            pools.add(pool);
        }
        pool.amount += it.getAmount();
        if (src != null) (chest ? pool.chestSources : pool.invSources).add(src);
    }

    /** Removes `amount` items of the pool's kind from its sources and returns them as one stack. */
    ItemStack take(Pool pool, int amount, boolean chestsFirst) {
        int left = amount;
        List<Source> order = new ArrayList<>();
        if (chestsFirst) {
            order.addAll(pool.chestSources);
            order.addAll(pool.invSources);
        } else {
            order.addAll(pool.invSources);
            order.addAll(pool.chestSources);
        }
        for (Source s : order) {
            if (left <= 0) break;
            ItemStack it = s.inv().getItem(s.slot());
            if (it == null || !pool.key.isSimilar(it)) continue;
            int use = Math.min(left, it.getAmount());
            if (use >= it.getAmount()) s.inv().setItem(s.slot(), null);
            else {
                it.setAmount(it.getAmount() - use);
                s.inv().setItem(s.slot(), it);
            }
            left -= use;
        }
        int got = amount - left;
        return got <= 0 ? null : pool.key.asQuantity(got);
    }

    boolean chestsFirst() {
        return !"INVENTORY_FIRST".equalsIgnoreCase(plugin.getConfig().getString("take-order", "CHESTS_FIRST"));
    }

    /** Most times this recipe can be crafted from these pools (menu crafting, no stack limit), up to cap. */
    int maxCrafts(List<Need> needs, List<Pool> pools, int cap) {
        if (allocate(needs, pools, 1, false) == null) return 0;
        int lo = 1, hi = Math.max(1, cap);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (allocate(needs, pools, mid, false) != null) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /**
     * Crafts a recipe `times` times straight from inventory + Magic Chests (no grid).
     * Returns how many times it was crafted (0 = not enough items).
     */
    int craft(Player player, Recipe recipe, List<Need> needs, int times, List<Block> chests) {
        if (times <= 0) return 0;
        List<Pool> pools = pools(player, chests, null);
        int n = Math.min(times, maxCrafts(needs, pools, times));
        if (n <= 0) return 0;
        int[] alloc = allocate(needs, pools, n, false);
        if (alloc == null) return 0;
        boolean first = chestsFirst();
        List<ItemStack> giveBack = new ArrayList<>();
        int[] taken = new int[needs.size()];
        for (int i = 0; i < needs.size(); i++) {
            ItemStack got = take(pools.get(alloc[i]), n, first);
            taken[i] = got == null ? 0 : got.getAmount();
            if (got != null && got.getType().getCraftingRemainingItem() != null) {
                giveBack.add(new ItemStack(got.getType().getCraftingRemainingItem(), got.getAmount()));
            }
            if (taken[i] < n) {
                // Something changed in between (should not happen on the main thread): undo and stop.
                for (int j = 0; j <= i; j++) {
                    if (taken[j] > 0) give(player, pools.get(alloc[j]).key.asQuantity(taken[j]), chests);
                }
                return 0;
            }
        }
        ItemStack result = recipe.getResult();
        int total = result.getAmount() * n;
        int max = Math.max(1, result.getMaxStackSize());
        while (total > 0) {
            int amt = Math.min(max, total);
            give(player, result.asQuantity(amt), chests);
            total -= amt;
        }
        for (ItemStack rest : giveBack) give(player, rest, chests);
        return n;
    }

    /** Gives an item: inventory first, then a Magic Chest, then on the ground. */
    void give(Player player, ItemStack item, List<Block> chests) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            for (Block b : chests) {
                if (rest == null) break;
                if (b.getState(false) instanceof Chest chest) {
                    Map<Integer, ItemStack> l2 = chest.getBlockInventory().addItem(rest);
                    rest = l2.isEmpty() ? null : l2.values().iterator().next();
                }
            }
            if (rest != null) player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** Puts the grid's items back: inventory first, then a Magic Chest, then on the ground. */
    void returnGrid(Player player, CraftingInventory grid, List<Block> chests) {
        ItemStack[] matrix = grid.getMatrix();
        grid.setMatrix(new ItemStack[matrix.length]);
        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(it);
            for (ItemStack rest : left.values()) {
                for (Block b : chests) {
                    if (rest == null) break;
                    if (b.getState(false) instanceof Chest chest) {
                        Map<Integer, ItemStack> l2 = chest.getBlockInventory().addItem(rest);
                        rest = l2.isEmpty() ? null : l2.values().iterator().next();
                    }
                }
                if (rest != null) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }
}
