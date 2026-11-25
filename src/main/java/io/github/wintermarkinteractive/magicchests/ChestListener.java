package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.List;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/** Placing, breaking, opening and protecting Magic Chests. */
public final class ChestListener implements Listener {

    private static final String LIMIT_PREFIX = "wintermark.magicchests.limit.";

    private final MagicChestsPlugin plugin;

    public ChestListener(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (b.getType() != Material.CHEST) return;
        Player p = e.getPlayer();
        ChestRegistry reg = plugin.registry();

        if (!plugin.items().isMagic(e.getItemInHand())) {
            // A normal chest where an old Magic Chest entry was left behind (e.g. world edits).
            if (reg.get(b) != null) reg.remove(MagicChest.Key.of(b));
            Bukkit.getScheduler().runTask(plugin, () -> separate(b));
            return;
        }
        if (!p.hasPermission("wintermark.magicchests.use")) {
            e.setCancelled(true);
            plugin.messages().send(p, "no-permission");
            return;
        }
        int max = limit(p);
        if (max > 0 && reg.count(p.getUniqueId()) >= max) {
            e.setCancelled(true);
            plugin.messages().send(p, "limit-reached", text("max", String.valueOf(max)));
            return;
        }
        reg.add(new MagicChest(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), p.getUniqueId(),
                System.currentTimeMillis()));
        plugin.messages().send(p, "placed",
                text("range", String.valueOf(plugin.getConfig().getInt("range.horizontal", 16))));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (b.getType() != Material.CHEST || reg.get(b) == null) return;
            BlockState state = b.getState();
            if (state instanceof Chest chest) {
                chest.customName(plugin.items().title());
                chest.update();
            }
            separate(b);
        });
    }

    /** A Magic Chest only joins another Magic Chest of the same owner; anything else becomes two single chests. */
    private void separate(Block b) {
        if (b.getType() != Material.CHEST) return;
        if (!(b.getState(false) instanceof Chest chest)) return;
        InventoryHolder holder = chest.getInventory().getHolder(false);
        if (!(holder instanceof DoubleChest dc)) return;
        Block other = null;
        for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(false), dc.getRightSide(false)}) {
            if (side instanceof Chest c && !c.getBlock().equals(b)) other = c.getBlock();
        }
        if (other == null) return;
        MagicChest a = plugin.registry().get(b);
        MagicChest o = plugin.registry().get(other);
        boolean ok = (a == null && o == null) || (a != null && o != null && a.owner().equals(o.owner()));
        if (ok) return;
        single(b);
        single(other);
    }

    private static void single(Block b) {
        BlockData data = b.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest cd && cd.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            cd.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            b.setBlockData(cd, false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        MagicChest c = plugin.registry().get(b);
        if (c == null) return;
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(c.owner()) && !p.hasPermission("wintermark.magicchests.admin")) {
            e.setCancelled(true);
            plugin.messages().send(p, "cant-break", text("owner", name(c)));
            return;
        }
        plugin.registry().remove(c.key());
        // setDropItems(false) also stops the contents from dropping, so empty this half first and drop
        // its items ourselves; then drop a Magic Chest instead of a plain chest.
        org.bukkit.Location at = b.getLocation().add(0.5, 0.5, 0.5);
        if (b.getState(false) instanceof Chest chest) {
            org.bukkit.inventory.Inventory inv = chest.getBlockInventory();
            ItemStack[] contents = inv.getContents();
            inv.clear();
            for (ItemStack it : contents) {
                if (it != null && !it.getType().isAir()) b.getWorld().dropItemNaturally(at, it);
            }
        }
        e.setDropItems(false);
        if (p.getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(at, plugin.items().create(1));
        }
        plugin.messages().send(p, "broken");
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!plugin.getConfig().getBoolean("protect-contents", true)) return;
        if (p.hasPermission("wintermark.magicchests.admin")) return;
        InventoryHolder holder = e.getInventory().getHolder(false);
        List<Block> blocks = new ArrayList<>(2);
        if (holder instanceof Chest c) blocks.add(c.getBlock());
        else if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide(false) instanceof Chest l) blocks.add(l.getBlock());
            if (dc.getRightSide(false) instanceof Chest r) blocks.add(r.getBlock());
        }
        for (Block b : blocks) {
            MagicChest m = plugin.registry().get(b);
            if (m != null && !plugin.registry().canUse(p.getUniqueId(), m.owner())) {
                e.setCancelled(true);
                plugin.messages().send(p, "not-yours", text("owner", name(m)));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(e.blockList());
    }

    private void explode(List<Block> blocks) {
        boolean proof = plugin.getConfig().getBoolean("explosion-proof", true);
        blocks.removeIf(b -> {
            MagicChest c = plugin.registry().get(b);
            if (c == null) return false;
            if (proof) return true;
            plugin.registry().remove(c.key());
            return false;
        });
    }

    /** Magic Chests are not a crafting ingredient (they would be lost as a plain chest). */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (plugin.items().isMagic(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("recipe.enabled", true)
                && e.getPlayer().hasPermission("wintermark.magicchests.use")) {
            e.getPlayer().discoverRecipe(plugin.items().recipeKey());
        }
    }

    private int limit(Player p) {
        int max = plugin.getConfig().getInt("max-chests", 0);
        if (max <= 0) return 0;
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith(LIMIT_PREFIX)) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring(LIMIT_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    static String name(MagicChest c) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(c.owner());
        return op.getName() == null ? c.owner().toString().substring(0, 8) : op.getName();
    }
}
