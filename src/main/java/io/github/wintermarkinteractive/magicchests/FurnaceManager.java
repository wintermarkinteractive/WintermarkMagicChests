package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/**
 * Furnaces, smokers and blast furnaces that smelt from Magic Chests: every second each queue tops up the
 * input and fuel from the owner's chests near the furnace and (optionally) moves finished items back.
 */
public final class FurnaceManager implements Runnable {

    private final MagicChestsPlugin plugin;
    private final Map<MagicChest.Key, FurnaceQueue> queues = new HashMap<>();
    private final Map<Material, List<CookingRecipe<?>>> recipes = new EnumMap<>(Material.class);
    private final Set<Material> fuels = new LinkedHashSet<>();

    public FurnaceManager(MagicChestsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void load(List<FurnaceQueue> list) {
        for (FurnaceQueue q : list) queues.put(q.key(), q);
    }

    public void reload() {
        recipes.clear();
        fuels.clear();
        for (String raw : plugin.getConfig().getStringList("furnace.fuels")) {
            String s = raw.trim();
            if (s.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(s.substring(1).toLowerCase(Locale.ROOT));
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag == null) plugin.getLogger().warning("Unknown item tag in furnace.fuels: " + s);
                else fuels.addAll(tag.getValues());
            } else {
                Material m = Material.matchMaterial(s.toUpperCase(Locale.ROOT));
                if (m == null) plugin.getLogger().warning("Unknown material in furnace.fuels: " + s);
                else fuels.add(m);
            }
        }
    }

    public static boolean isStation(Material m) {
        return m == Material.FURNACE || m == Material.SMOKER || m == Material.BLAST_FURNACE;
    }

    public FurnaceQueue get(Block b) {
        return queues.get(MagicChest.Key.of(b));
    }

    public int count() {
        return queues.size();
    }

    /** Cooking recipes of this station type (furnace, smoker or blast furnace). */
    private List<CookingRecipe<?>> recipesFor(Material station) {
        return recipes.computeIfAbsent(station, st -> {
            List<CookingRecipe<?>> list = new ArrayList<>();
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                boolean ok = switch (st) {
                    case FURNACE -> r instanceof FurnaceRecipe;
                    case SMOKER -> r instanceof SmokingRecipe;
                    case BLAST_FURNACE -> r instanceof BlastingRecipe;
                    default -> false;
                };
                if (ok) list.add((CookingRecipe<?>) r);
            }
            return list;
        });
    }

    /** What this station makes from the item, or null if it can't cook it. */
    public ItemStack resultOf(Material station, Material input) {
        ItemStack in = new ItemStack(input);
        for (CookingRecipe<?> r : recipesFor(station)) {
            if (r.getInputChoice().test(in)) return r.getResult();
        }
        return null;
    }

    /** Plain items in these chests, by type (insertion order = nearest chest first). */
    public static Map<Material, Integer> contents(List<Block> chests) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        for (Block b : chests) {
            if (!(b.getState(false) instanceof Chest chest)) continue;
            for (ItemStack it : chest.getBlockInventory().getContents()) {
                if (it != null && MagicItems.isPlain(it)) map.merge(it.getType(), it.getAmount(), Integer::sum);
            }
        }
        return map;
    }

    /** Removes up to `amount` plain items of this type from the chests; returns how many were taken. */
    static int take(List<Block> chests, Material type, int amount) {
        int left = amount;
        for (Block b : chests) {
            if (left <= 0) break;
            if (!(b.getState(false) instanceof Chest chest)) continue;
            Inventory inv = chest.getBlockInventory();
            for (int slot = 0; slot < inv.getSize() && left > 0; slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null || it.getType() != type || !MagicItems.isPlain(it)) continue;
                int use = Math.min(left, it.getAmount());
                if (use >= it.getAmount()) inv.setItem(slot, null);
                else {
                    it.setAmount(it.getAmount() - use);
                    inv.setItem(slot, it);
                }
                left -= use;
            }
        }
        return amount - left;
    }

    /** Puts an item into the chests; returns what didn't fit (or null). */
    static ItemStack store(List<Block> chests, ItemStack item) {
        ItemStack rest = item;
        for (Block b : chests) {
            if (rest == null) break;
            if (b.getState(false) instanceof Chest chest) {
                Map<Integer, ItemStack> left = chest.getBlockInventory().addItem(rest);
                rest = left.isEmpty() ? null : left.values().iterator().next();
            }
        }
        return rest;
    }

    public enum StartResult { STARTED, NONE_IN_CHESTS, BUSY, IN_USE }

    /** Starts (or replaces) a queue for this furnace. amount <= 0 = everything in the chests. */
    public StartResult start(Player p, Block furnace, Material input, int amount, boolean output) {
        MagicChest.Key key = MagicChest.Key.of(furnace);
        FurnaceQueue old = queues.get(key);
        if (old != null && !old.owner().equals(p.getUniqueId()) && !p.hasPermission("wintermark.magicchests.admin")) {
            return StartResult.IN_USE;
        }
        if (!(furnace.getState(false) instanceof Furnace f)) return StartResult.BUSY;
        ItemStack smelting = f.getInventory().getSmelting();
        if (smelting != null && !smelting.getType().isAir()
                && (smelting.getType() != input || !MagicItems.isPlain(smelting))) {
            return StartResult.BUSY;
        }
        List<Block> chests = plugin.registry().usable(p, furnace.getLocation());
        int available = contents(chests).getOrDefault(input, 0);
        if (available <= 0) return StartResult.NONE_IN_CHESTS;
        int n = amount <= 0 ? available : Math.min(amount, available);
        FurnaceQueue q = new FurnaceQueue(key, p.getUniqueId(), input, n, n, output);
        queues.put(key, q);
        plugin.database().saveQueue(q);
        process(q);
        return StartResult.STARTED;
    }

    public void stop(MagicChest.Key key) {
        if (queues.remove(key) != null) plugin.database().deleteQueue(key);
    }

    public void setOutput(FurnaceQueue q, boolean output) {
        q.output(output);
        plugin.database().saveQueue(q);
    }

    @Override
    public void run() {
        for (FurnaceQueue q : new ArrayList<>(queues.values())) process(q);
    }

    private void process(FurnaceQueue q) {
        World w = Bukkit.getWorld(q.key().world());
        if (w == null) return;
        int cx = q.key().x() >> 4, cz = q.key().z() >> 4;
        if (!w.isChunkLoaded(cx, cz)) return; // paused while nobody is near
        Block b = w.getBlockAt(q.key().x(), q.key().y(), q.key().z());
        if (!isStation(b.getType()) || !(b.getState(false) instanceof Furnace furnace)) {
            stop(q.key());
            return;
        }
        FurnaceInventory inv = furnace.getInventory();
        List<Block> chests = plugin.registry().usable(q.owner(), b.getLocation());
        int before = q.remaining();

        // 1. Finished items back into a Magic Chest.
        ItemStack result = inv.getResult();
        if (q.output() && result != null && !result.getType().isAir() && !chests.isEmpty()) {
            inv.setResult(store(chests, result.clone()));
        }

        // 2. Top up the input slot.
        ItemStack smelting = inv.getSmelting();
        boolean empty = smelting == null || smelting.getType().isAir();
        boolean ours = !empty && smelting.getType() == q.input() && MagicItems.isPlain(smelting);
        if ((empty || ours) && q.remaining() > 0) {
            int have = empty ? 0 : smelting.getAmount();
            int want = Math.min(q.input().getMaxStackSize() - have, q.remaining());
            if (want > 0) {
                int got = take(chests, q.input(), want);
                if (got > 0) {
                    inv.setSmelting(new ItemStack(q.input(), have + got));
                    q.remaining(q.remaining() - got);
                    empty = false;
                } else if (empty) {
                    finish(q, "smelt-out");
                    return;
                }
            }
        }

        // 3. Top up the fuel slot while there is something to cook.
        if (!empty) feedFuel(q, furnace, inv, chests);

        // 4. Done when everything was loaded and the input slot is empty.
        smelting = inv.getSmelting();
        if (q.remaining() <= 0 && (smelting == null || smelting.getType().isAir())) {
            finish(q, "smelt-done");
            return;
        }
        if (q.remaining() != before) plugin.database().saveQueue(q);
    }

    private void feedFuel(FurnaceQueue q, Furnace furnace, FurnaceInventory inv, List<Block> chests) {
        ItemStack fuel = inv.getFuel();
        Material type = null;
        int have = 0;
        if (fuel != null && !fuel.getType().isAir()) {
            if (!fuels.contains(fuel.getType()) || !MagicItems.isPlain(fuel)) return; // someone else's fuel: leave it
            type = fuel.getType();
            have = fuel.getAmount();
            if (have >= type.getMaxStackSize()) return;
        } else {
            Map<Material, Integer> in = contents(chests);
            for (Material m : fuels) {
                if (in.getOrDefault(m, 0) > 0) {
                    type = m;
                    break;
                }
            }
        }
        if (type == null) {
            if (furnace.getBurnTime() <= 0 && !q.warnedFuel) {
                q.warnedFuel = true;
                notify(q, "smelt-no-fuel");
            }
            return;
        }
        int got = take(chests, type, type.getMaxStackSize() - have);
        if (got > 0) {
            inv.setFuel(new ItemStack(type, have + got));
            q.warnedFuel = false;
        } else if (have == 0 && furnace.getBurnTime() <= 0 && !q.warnedFuel) {
            q.warnedFuel = true;
            notify(q, "smelt-no-fuel");
        }
    }

    private void finish(FurnaceQueue q, String message) {
        stop(q.key());
        notify(q, message);
    }

    private void notify(FurnaceQueue q, String key) {
        Player p = Bukkit.getPlayer(q.owner());
        if (p == null) return;
        plugin.messages().send(p, key, text("item", nice(q.input())),
                text("amount", String.valueOf(q.total())),
                text("x", String.valueOf(q.key().x())), text("y", String.valueOf(q.key().y())),
                text("z", String.valueOf(q.key().z())));
    }

    static String nice(Material m) {
        return m.getKey().getKey().replace('_', ' ');
    }
}
