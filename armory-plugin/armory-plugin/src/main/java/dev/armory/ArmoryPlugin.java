package dev.armory;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArmoryPlugin extends JavaPlugin {

    private Abilities abilities;
    private WeaponManager weapons;
    private RecipeManager recipes;
    private ArmoryBrowser browser;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        abilities = new Abilities(this);
        weapons = new WeaponManager(this);
        recipes = new RecipeManager(this);
        browser = new ArmoryBrowser(this);

        weapons.load();
        recipes.register();

        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(browser, this);

        PluginCommand command = getCommand("armory");
        if (command != null) {
            ArmoryCommand handler = new ArmoryCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getLogger().info("Loaded " + weapons.all().size() + " weapons.");
    }

    @Override
    public void onDisable() {
        if (recipes != null) {
            recipes.unregister();
        }
    }

    public void reload() {
        reloadConfig();
        weapons.load();
        recipes.register();
    }

    public Abilities abilities() {
        return abilities;
    }

    public WeaponManager weapons() {
        return weapons;
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public ArmoryBrowser browser() {
        return browser;
    }
}
