package io.github.wintermarkinteractive.magicchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** MiniMessage texts from config.yml (messages.*). An empty text means "don't send". */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FileConfiguration cfg;
    private final TagResolver prefix;

    public Messages(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = Placeholder.parsed("prefix", cfg.getString("messages.prefix", ""));
    }

    public void send(CommandSender to, String key, TagResolver... tags) {
        if (to == null) return;
        Component c = render(key, tags);
        if (c != null) to.sendMessage(c);
    }

    /** Returns null when the text is empty or missing. */
    public Component render(String key, TagResolver... tags) {
        String raw = cfg.getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) return null;
        TagResolver all = TagResolver.builder().resolver(prefix).resolvers(tags).build();
        return MM.deserialize(raw, all);
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value == null ? Component.empty() : value);
    }

    public static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }
}
