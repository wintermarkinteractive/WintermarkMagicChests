package io.github.wintermarkinteractive.magicchests;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Native text-input dialog for searching the Storage Crafting menu. */
public final class SearchDialog {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SearchDialog() { }

    public static void open(MagicChestsPlugin plugin, Player player, Location station, CraftMenu.State state) {
        open(plugin, player, "search-dialog.", state.search(),
                q -> CraftMenu.open(plugin, player, station, state.search(q)),
                () -> CraftMenu.open(plugin, player, station, state));
    }

    /** Generic search box: `done` gets the typed text, `back` runs on cancel (both on the main thread). */
    public static void open(MagicChestsPlugin plugin, Player player, String p, String initial,
                            Consumer<String> done, Runnable back) {
        var cfg = plugin.getConfig();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(cfg.getString(p + "title", "Search")))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(MM.deserialize(cfg.getString(p + "body", "")), 250)))
                        .inputs(List.of(DialogInput.text("query", MM.deserialize(cfg.getString(p + "input-label", "Item name")))
                                .width(220)
                                .maxLength(32)
                                .initial(initial == null ? "" : initial)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(MM.deserialize(cfg.getString(p + "confirm", "Search")))
                                .width(100)
                                .action(DialogAction.customClick((view, audience) -> {
                                    String q = view.getText("query");
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (player.isOnline()) done.accept(q);
                                    });
                                }, once))
                                .build(),
                        ActionButton.builder(MM.deserialize(cfg.getString(p + "cancel", "Back")))
                                .width(100)
                                .action(DialogAction.customClick((view, audience) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (player.isOnline()) back.run();
                                        }), once))
                                .build())));
        player.closeInventory();
        player.showDialog(dialog);
    }
}
