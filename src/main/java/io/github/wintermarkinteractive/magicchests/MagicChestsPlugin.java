package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class MagicChestsPlugin extends JavaPlugin {

    private Database database;
    private ChestRegistry registry;
    private MagicItems items;
    private RecipeFiller filler;
    private FurnaceManager furnaces;
    private StationSessions stations;
    private BukkitTask furnaceTask;
    private ParticleTask particles;
    private BukkitTask particleTask;
    private volatile Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        addMissingConfigKeys();
        messages = new Messages(getConfig());
        items = new MagicItems(this);
        filler = new RecipeFiller(this);
        registry = new ChestRegistry(this);

        database = new Database(new File(getDataFolder(), "magicchests.db"), getLogger());
        try {
            database.open();
            registry.load(database.loadChests(), database.loadTrust());
            furnaces = new FurnaceManager(this);
            furnaces.load(database.loadQueues());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open magicchests.db - disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        items.registerRecipe();
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new FurnaceListener(this), this);
        stations = new StationSessions(this);
        getServer().getPluginManager().registerEvents(stations, this);
        registerCommand("magicchests", "Magic Chests: trust friends, list your chests",
                List.of("magicchest", "mchest"), new MagicChestsCommand(this));
        registerCommand("storage", "Everything in your Magic Chests in one menu",
                List.of("magicstorage"), new StorageCommand(this));

        particles = new ParticleTask(this);
        particleTask = getServer().getScheduler().runTaskTimer(this, particles, 40L, 10L);
        furnaceTask = getServer().getScheduler().runTaskTimer(this, furnaces, 60L, 20L);

        if (getConfig().getBoolean("recipe.enabled", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("wintermark.magicchests.use")) p.discoverRecipe(items.recipeKey());
            }
        }
        getLogger().info(registry.total() + " Magic Chests and " + furnaces.count() + " furnace queues loaded.");
    }

    @Override
    public void onDisable() {
        if (particleTask != null) particleTask.cancel();
        if (furnaceTask != null) furnaceTask.cancel();
        if (items != null) Bukkit.removeRecipe(items.recipeKey());
        if (database != null) database.close();
    }

    public void reload() {
        reloadConfig();
        addMissingConfigKeys();
        messages = new Messages(getConfig());
        items.registerRecipe();
        particles.reload();
        CraftMenu.clearCache();
        furnaces.reload();
        StationMenu.clearCache();
    }

    private void addMissingConfigKeys() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration defaults;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaults = YamlConfiguration.loadConfiguration(r);
            }
            FileConfiguration cfg = getConfig();
            Set<String> newSections = new HashSet<>();
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) && !cfg.contains(key, true)) newSections.add(key);
            }
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue;
                // The recipe belongs to the server owner once it exists: don't add default letters to it.
                if (key.startsWith("recipe.ingredients.") && cfg.isConfigurationSection("recipe.ingredients")) continue;
                if (!cfg.contains(key, true)) {
                    cfg.set(key, defaults.get(key));
                    cfg.setComments(key, defaults.getComments(key));
                    changed = true;
                }
            }
            for (String key : newSections) cfg.setComments(key, defaults.getComments(key));
            if (changed) {
                saveConfig();
                reloadConfig();
                getLogger().info("Added new settings to config.yml.");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Could not update config.yml with new keys", e);
        }
    }


    public Database database() { return database; }
    public ChestRegistry registry() { return registry; }
    public MagicItems items() { return items; }
    public RecipeFiller filler() { return filler; }
    public FurnaceManager furnaces() { return furnaces; }
    public StationSessions stations() { return stations; }
    public Messages messages() { return messages; }
}
