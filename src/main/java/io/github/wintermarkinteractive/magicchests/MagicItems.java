package io.github.wintermarkinteractive.magicchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Magic Chest item, its recipe and the "plain item" check. */
public final class MagicItems {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MagicChestsPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;

    public MagicItems(MagicChestsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "magic");
        this.recipeKey = new NamespacedKey(plugin, "magic_chest");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.CHEST, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(plugin.getConfig().getString("item.name", "<light_purple>Magic Chest"))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("item.lore")) lore.add(noItalic(MM.deserialize(line)));
        meta.lore(lore);
        if (plugin.getConfig().getBoolean("item.glow", true)) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMagic(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** True for items without custom data: no name, lore, enchantments, damage or plugin data. */
    public static boolean isPlain(ItemStack item) {
        return item != null && !item.getType().isAir() && item.isSimilar(ItemStack.of(item.getType()));
    }

    /** Items that crafting must never touch: Magic Chests and other plugins' items (PDC). */
    public boolean isProtected(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        if (isMagic(item)) return true;
        return item.hasItemMeta() && !item.getItemMeta().getPersistentDataContainer().isEmpty();
    }

    public Component title() {
        return MM.deserialize(plugin.getConfig().getString("item.title", "<dark_purple>Magic Chest"));
    }

    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        if (!plugin.getConfig().getBoolean("recipe.enabled", true)) return;
        List<String> shape = plugin.getConfig().getStringList("recipe.shape");
        ConfigurationSection ing = plugin.getConfig().getConfigurationSection("recipe.ingredients");
        if (shape.isEmpty() || shape.size() > 3 || ing == null) {
            plugin.getLogger().warning("recipe.shape / recipe.ingredients are invalid - no Magic Chest recipe.");
            return;
        }
        try {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(1));
            recipe.shape(shape.toArray(new String[0]));
            String letters = String.join("", shape);
            for (String k : ing.getKeys(false)) {
                if (k.length() != 1 || letters.indexOf(k.charAt(0)) < 0) continue;
                Material m = Material.matchMaterial(ing.getString(k, "").toUpperCase(Locale.ROOT));
                if (m == null || k.length() != 1) {
                    plugin.getLogger().warning("Unknown recipe ingredient " + k + ": " + ing.getString(k));
                    return;
                }
                recipe.setIngredient(k.charAt(0), m);
            }
            Bukkit.addRecipe(recipe);
            if (!Bukkit.getOnlinePlayers().isEmpty()) Bukkit.updateRecipes();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register the Magic Chest recipe: " + e.getMessage());
        }
    }

    static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
