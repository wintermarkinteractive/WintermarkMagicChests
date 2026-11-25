package io.github.wintermarkinteractive.magicchests;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.github.wintermarkinteractive.magicchests.Messages.text;

/**
 * /magicchests trust|untrust <player>, trusted, list, info
 * /magicchests give <player> [amount], reload   (admin)
 */
public final class MagicChestsCommand implements BasicCommand {

    private static final String ADMIN = "wintermark.magicchests.admin";

    private final MagicChestsPlugin plugin;

    public MagicChestsCommand(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Messages msg = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    msg.send(sender, "no-permission");
                    return;
                }
                plugin.reload();
                msg.send(sender, "reloaded");
            }
            case "trust", "untrust" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "players-only");
                    return;
                }
                if (args.length < 2) {
                    help(sender);
                    return;
                }
                OfflinePlayer other = find(args[1]);
                if (other == null) {
                    msg.send(sender, "player-not-found", text("player", args[1]));
                    return;
                }
                String name = other.getName() == null ? args[1] : other.getName();
                if (other.getUniqueId().equals(p.getUniqueId())) {
                    msg.send(sender, "trust-self");
                    return;
                }
                if (sub.equals("trust")) {
                    msg.send(sender, plugin.registry().trust(p.getUniqueId(), other.getUniqueId())
                            ? "trusted-added" : "trusted-already", text("player", name));
                } else {
                    msg.send(sender, plugin.registry().untrust(p.getUniqueId(), other.getUniqueId())
                            ? "trusted-removed" : "trusted-not", text("player", name));
                }
            }
            case "trusted" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "players-only");
                    return;
                }
                Collection<UUID> list = plugin.registry().trusted(p.getUniqueId());
                if (list.isEmpty()) {
                    msg.send(sender, "trusted-none");
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID id : list) {
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    names.add(n == null ? id.toString().substring(0, 8) : n);
                }
                msg.send(sender, "trusted-header", text("list", String.join(", ", names)));
            }
            case "list" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "players-only");
                    return;
                }
                List<MagicChest> own = plugin.registry().ownedBy(p.getUniqueId());
                if (own.isEmpty()) {
                    msg.send(sender, "list-none");
                    return;
                }
                msg.send(sender, "list-header", text("count", String.valueOf(own.size())));
                int shown = 0;
                for (MagicChest c : own) {
                    if (shown++ >= 10) break;
                    World w = Bukkit.getWorld(c.world());
                    msg.send(sender, "list-line", text("world", w == null ? "?" : w.getName()),
                            text("x", String.valueOf(c.x())), text("y", String.valueOf(c.y())),
                            text("z", String.valueOf(c.z())));
                }
                if (own.size() > 10) msg.send(sender, "list-more", text("more", String.valueOf(own.size() - 10)));
            }
            case "info" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "players-only");
                    return;
                }
                Block b = p.getTargetBlockExact(6);
                MagicChest c = plugin.registry().get(b);
                if (c == null) {
                    msg.send(sender, "info-none");
                    return;
                }
                boolean use = plugin.registry().canUse(p.getUniqueId(), c.owner());
                msg.send(sender, "info", text("owner", ChestListener.name(c)),
                        text("x", String.valueOf(c.x())), text("y", String.valueOf(c.y())),
                        text("z", String.valueOf(c.z())), text("use", use ? "yes" : "no"));
            }
            default -> help(sender);
        }
    }

    private void give(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission(ADMIN)) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            help(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found", text("player", args[1]));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                help(sender);
                return;
            }
        }
        ItemStack item = plugin.items().create(amount);
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        for (ItemStack rest : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), rest);
        msg.send(sender, "given", text("amount", String.valueOf(amount)), text("player", target.getName()));
        if (sender != target) msg.send(target, "received", text("amount", String.valueOf(amount)));
    }

    private void help(CommandSender sender) {
        plugin.messages().send(sender, "help");
        if (sender.hasPermission(ADMIN)) plugin.messages().send(sender, "help-admin");
    }

    private static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        int pos = Math.max(1, args.length);
        boolean admin = source.getSender().hasPermission(ADMIN);
        List<String> out = new ArrayList<>();
        if (pos == 1) {
            List<String> subs = new ArrayList<>(List.of("trust", "untrust", "trusted", "list", "info", "help"));
            if (admin) {
                subs.add("give");
                subs.add("reload");
            }
            for (String s : subs) if (s.startsWith(last)) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (pos == 2 && (sub.equals("trust") || (sub.equals("give") && admin))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        } else if (pos == 2 && sub.equals("untrust") && source.getSender() instanceof Player p) {
            for (UUID id : plugin.registry().trusted(p.getUniqueId())) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(last)) out.add(n);
            }
        } else if (pos == 3 && sub.equals("give") && admin) {
            out.addAll(List.of("1", "4", "16"));
        }
        return out;
    }

    @Override
    public String permission() {
        return "wintermark.magicchests.use";
    }
}
