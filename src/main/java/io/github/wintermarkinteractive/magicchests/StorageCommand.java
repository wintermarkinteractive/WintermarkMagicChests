package io.github.wintermarkinteractive.magicchests;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

/** /storage: everything in your Magic Chests in one menu. */
public final class StorageCommand implements BasicCommand {

    private final MagicChestsPlugin plugin;

    public StorageCommand(MagicChestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player p)) {
            plugin.messages().send(source.getSender(), "players-only");
            return;
        }
        StorageMenu.State state = StorageMenu.State.start();
        if (args.length > 0) state = state.search(String.join(" ", args));
        StorageMenu.open(plugin, p, state);
    }

    @Override
    public String permission() {
        return "wintermark.magicchests.storage";
    }
}
