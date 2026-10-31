package city.cube.optimization.gui;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.util.Messages;
import city.cube.optimization.util.Numbers;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Admin control panel: every value here writes straight back to config.yml. */
public final class GuiManager implements Listener {

    private static final int SIZE = 45;

    private final CubeCityOptimization plugin;

    public GuiManager(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!plugin.settings().gui.enabled) {
            plugin.message(player, "<red>The GUI is disabled in config.yml.</red>");
            return;
        }
        PluginConfig.Settings settings = plugin.settings();
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Messages.parse("<dark_aqua><bold>" + settings.gui.title));
        holder.inventory = inventory;

        inventory.setItem(4, statusItem());
        inventory.setItem(10, toggleItem(Material.GRASS_BLOCK, "Chunk optimizer", settings.chunk.enabled,
                List.of("View distance: <white>" + settings.chunk.viewDistance + "</white>",
                        "Simulation: <white>" + settings.chunk.simulationDistance + "</white>",
                        "Budget: <white>" + settings.chunk.maxChunksPerPlayer + "</white> chunks"),
                "chunks.enabled"));
        inventory.setItem(11, numberItem(Material.SPYGLASS, "View distance", settings.chunk.viewDistance,
                "chunks.view-distance",
                "Per-player render distance.", "Left-click: +1   Right-click: -1"));
        inventory.setItem(12, toggleItem(Material.HOPPER, "Hopper optimizer", settings.hopper.enabled,
                List.of("Cooldown: <white>" + settings.hopper.transferCooldownTicks + " ticks</white>",
                        "Max per chunk: <white>" + settings.hopper.maxPerChunk + "</white>",
                        "Throttled: <white>" + Numbers.ints(plugin.stats().get(Stats.HOPPERS_THROTTLED)) + "</white>"),
                "hoppers.enabled"));
        inventory.setItem(13, numberItem(Material.CLOCK, "Hopper cooldown", settings.hopper.transferCooldownTicks,
                "hoppers.transfer-cooldown-ticks",
                "Observable minimum ticks between transfers.", "Left-click: +1   Right-click: -1"));
        inventory.setItem(14, numberItem(Material.CHEST, "Hoppers per chunk", settings.hopper.maxPerChunk,
                "hoppers.max-per-chunk",
                "0 disables the density limit.", "Left-click: +1   Right-click: -1"));

        inventory.setItem(15, toggleItem(Material.DISPENSER, "Dispenser optimizer", settings.dispenser.enabled,
                List.of("Cooldown: <white>" + settings.dispenser.cooldownTicks + " ticks</white>",
                        "Throttled: <white>" + Numbers.ints(plugin.stats().get(Stats.DISPENSERS_THROTTLED)) + "</white>"),
                "dispensers.enabled"));
        inventory.setItem(16, numberItem(Material.REPEATER, "Dispenser cooldown", settings.dispenser.cooldownTicks,
                "dispensers.cooldown-ticks",
                "0 disables the per-block cooldown.", "Left-click: +1   Right-click: -1"));

        inventory.setItem(19, toggleItem(Material.TNT, "TNT optimizer", settings.tnt.enabled,
                List.of("Merged: <white>" + Numbers.ints(plugin.stats().get(Stats.TNT_MERGED)) + "</white>",
                        "Saved blocks: <white>" + Numbers.ints(plugin.stats().get(Stats.BLOCKS_SAVED)) + "</white>"),
                "tnt.enabled"));
        inventory.setItem(20, numberItem(Material.FIRE_CHARGE, "TNT per chunk", settings.tnt.maxPerChunk,
                "tnt.max-per-chunk",
                "0 disables the limit.", "Left-click: +1   Right-click: -1"));

        inventory.setItem(21, toggleItem(Material.ZOMBIE_HEAD, "Mob optimizer", settings.mob.enabled,
                List.of("Removed: <white>" + Numbers.ints(plugin.stats().get(Stats.MOBS_REMOVED)) + "</white>",
                        "Spawn denials: <white>" + Numbers.ints(plugin.stats().get(Stats.SPAWNS_BLOCKED)) + "</white>"),
                "mobs.enabled"));
        inventory.setItem(22, numberItem(Material.ROTTEN_FLESH, "Mob cap (world)", settings.mob.globalCap,
                "mobs.global-cap",
                "0 disables the global cap.", "Left-click: +25   Right-click: -25"));
        inventory.setItem(23, numberItem(Material.SPIDER_EYE, "Mob cap (chunk)", settings.mob.perChunkCap,
                "mobs.per-chunk-cap",
                "0 disables the per-chunk cap.", "Left-click: +1   Right-click: -1"));

        inventory.setItem(24, actionItem(Material.BUCKET, "Run cleanup now",
                List.of("Removes old drops/orbs and dense chunks.",
                        "Unloaded chunks: <white>" + Numbers.ints(plugin.stats().get(Stats.CHUNKS_UNLOADED)) + "</white>"),
                NamedTextColor.GOLD));

        inventory.setItem(30, statusCard());
        inventory.setItem(32, storageItem());
        inventory.setItem(34, actionItem(Material.REDSTONE, "Reload config",
                List.of("Re-reads config.yml and restarts tasks."), NamedTextColor.RED));
        inventory.setItem(40, actionItem(Material.BARRIER, "Close", List.of(), NamedTextColor.GRAY));

        player.openInventory(inventory);
    }

    private ItemStack statusItem() {
        PluginConfig.Settings settings = plugin.settings();
        List<String> lore = new ArrayList<>();
        lore.add("TPS: " + Numbers.tpsText(plugin.tps().tps()));
        lore.add("MSPT: <white>" + Numbers.oneDecimal(plugin.tps().mspt()) + "ms</white>");
        lore.add("Pressure mode: <white>" + (plugin.tps().pressure() ? "on" : "off") + "</white>");
        lore.add("Online: <white>" + Bukkit.getOnlinePlayers().size() + "</white>");
        lore.add("Storage: " + (plugin.storage().ready()
                ? "<green>" + plugin.storage().describe() + "</green>"
                : "<red>disabled</red>"));
        return item(Material.CLOCK, "<aqua><bold>CubeCity Optimization</bold></aqua>", lore);
    }

    private ItemStack statusCard() {
        List<String> lore = new ArrayList<>();
        lore.add("This panel edits config.yml live.");
        lore.add("Left-click toggles, right-click lowers values.");
        lore.add("");
        lore.add("TPS: " + Numbers.tpsText(plugin.tps().tps()));
        return item(Material.BOOK, "<white>How to use</white>", lore);
    }

    private ItemStack storageItem() {
        List<String> lore = new ArrayList<>();
        lore.add("Backend: <white>" + plugin.settings().storage.type + "</white>");
        lore.add("Status: " + (plugin.storage().ready() ? "<green>connected</green>" : "<red>disabled</red>"));
        lore.add("Snapshots: every <white>" + plugin.settings().storage.saveIntervalSeconds + "s</white>");
        lore.add("Recent actions are pruned after <white>"
                + plugin.settings().storage.retentionDays + "d</white>");
        return item(Material.WRITABLE_BOOK, "<gold>Storage</gold>", lore);
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled, List<String> lore, String path) {
        List<String> lines = new ArrayList<>(lore);
        lines.add("");
        lines.add(enabled ? "<green>Enabled</green> <gray>— left-click to disable</gray>"
                : "<red>Disabled</red> <gray>— left-click to enable</gray>");
        return item(material, (enabled ? "<green>" : "<red>") + name, lines);
    }

    private ItemStack numberItem(Material material, String name, int value, String path,
                                 String description, String hint) {
        List<String> lines = new ArrayList<>();
        lines.add("Value: <white>" + value + "</white>");
        lines.add(description);
        lines.add("<gray>" + hint + "</gray>");
        lines.add("<dark_gray>Path: " + path + "</dark_gray>");
        return item(material, "<yellow>" + name + "</yellow>", lines);
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, NamedTextColor color) {
        List<String> lines = new ArrayList<>(lore);
        lines.add("");
        lines.add("<gray>Click to run</gray>");
        return item(material, color + name, lines);
    }

    private ItemStack item(Material material, String display, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.parse(display));
            meta.lore(lore.stream().map(Messages::parse).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) {
            return;
        }

        boolean right = event.isRightClick();
        int slot = event.getRawSlot();
        PluginConfig.Settings settings = plugin.settings();

        switch (slot) {
            case 10 -> update("chunks.enabled", !settings.chunk.enabled);
            case 11 -> bump("chunks.view-distance", settings.chunk.viewDistance, right ? -1 : 1, 2, 16);
            case 12 -> update("hoppers.enabled", !settings.hopper.enabled);
            case 13 -> bump("hoppers.transfer-cooldown-ticks", settings.hopper.transferCooldownTicks,
                    right ? -1 : 1, 1, 40);
            case 14 -> bump("hoppers.max-per-chunk", settings.hopper.maxPerChunk, right ? -1 : 1, 0, 128);
            case 15 -> update("dispensers.enabled", !settings.dispenser.enabled);
            case 16 -> bump("dispensers.cooldown-ticks", settings.dispenser.cooldownTicks,
                    right ? -1 : 1, 0, 100);
            case 19 -> update("tnt.enabled", !settings.tnt.enabled);
            case 20 -> bump("tnt.max-per-chunk", settings.tnt.maxPerChunk, right ? -1 : 1, 0, 128);
            case 21 -> update("mobs.enabled", !settings.mob.enabled);
            case 22 -> bump("mobs.global-cap", settings.mob.globalCap, right ? -25 : 25, 0, 5000);
            case 23 -> bump("mobs.per-chunk-cap", settings.mob.perChunkCap, right ? -1 : 1, 0, 64);
            case 24 -> {
                CleanupResult result = runCleanup(player);
                plugin.message(player, "<green>Cleanup finished:</green> "
                        + result.items + " items, " + result.orbs + " orbs, "
                        + result.entities + " entities, " + result.chunks + " chunks.");
            }
            case 34 -> {
                plugin.reloadEverything();
                plugin.message(player, "<green>Configuration reloaded.</green>");
            }
            case 40 -> player.closeInventory();
            default -> {
                // no-op slots
            }
        }

        if (slot != 40 && plugin.settings().gui.enabled) {
            // Reopening inside the click packet causes cursor desync; do it next tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    open(player);
                }
            });
        }
    }

    private CleanupResult runCleanup(Player player) {
        PluginConfig.EconomySettings economy = plugin.settings().economy;
        if (economy.enabled && economy.cleanupCost > 0.0 && plugin.economy().available()
                && !plugin.economy().charge(player, economy.cleanupCost)) {
            plugin.message(player, "<red>You cannot afford the cleanup fee of </red>"
                    + plugin.economy().format(economy.cleanupCost));
            return new CleanupResult(0, 0, 0, 0);
        }
        var report = plugin.cleanupService().runAll("gui:" + player.getName());
        return new CleanupResult(report.items(), report.orbs(), report.entities(), report.chunksUnloaded());
    }

    private void bump(String path, int current, int delta, int min, int max) {
        if (current < min || current > max) {
            // Value was hand-edited outside the GUI range: never silently shrink it.
            return;
        }
        int next = Numbers.clamp(current + delta, min, max);
        if (next != current) {
            update(path, next);
        }
    }

    private void update(String path, Object value) {
        plugin.config().update(path, value);
        plugin.restartTasks();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private record CleanupResult(int items, int orbs, int entities, int chunks) {
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
