package io.github.wintermarkinteractive.magicchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shared menu items: buttons with config texts, the grey filler, extra lore. */
final class MenuItems {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MenuItems() { }

    static ItemStack button(MagicChestsPlugin plugin, Material mat, String namePath, String lorePath, TagResolver tags) {
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

    static ItemStack filler() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        return filler;
    }

    /** A copy of the item with config lore lines added under its own lore. */
    static ItemStack withLore(MagicChestsPlugin plugin, ItemStack base, String lorePath, TagResolver tags) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : plugin.getConfig().getStringList(lorePath)) lore.add(MagicItems.noItalic(MM.deserialize(line, tags)));
        meta.lore(lore);
        item.setItemMeta(meta);
        CraftMenu.hideStats(item);
        return item;
    }

    static Component title(MagicChestsPlugin plugin, String path, String fallback, TagResolver tags) {
        return MM.deserialize(plugin.getConfig().getString(path, fallback), tags);
    }
}
