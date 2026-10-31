package city.cube.optimization.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Small Adventure helpers so every message supports MiniMessage formatting. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Messages() {
    }

    public static Component parse(String text) {
        return MINI.deserialize(text == null ? "" : text);
    }

    public static String escape(String input) {
        return MINI.escapeTags(input == null ? "" : input);
    }

    public static void send(CommandSender sender, String prefix, String body) {
        sender.sendMessage(parse(prefix + body));
    }
}
