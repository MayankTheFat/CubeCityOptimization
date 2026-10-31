package city.cube.optimization.hooks;

import city.cube.optimization.CubeCityOptimization;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Optional Vault bridge. Loaded reflectively-by-luck: the class is only touched
 * when Vault is installed, so servers without Vault never load Vault classes.
 */
public final class EconomyHook {

    private final CubeCityOptimization plugin;
    private Economy economy;

    public EconomyHook(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            this.economy = registration.getProvider();
            plugin.getLogger().info("Vault economy hooked: " + economy.getName());
        }
    }

    public boolean available() {
        return economy != null;
    }

    public String format(double amount) {
        return economy == null ? amount + "" : economy.format(amount);
    }

    public double balance(OfflinePlayer player) {
        return economy == null ? 0.0 : economy.getBalance(player);
    }

    public boolean charge(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0.0) {
            return true;
        }
        if (!economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
