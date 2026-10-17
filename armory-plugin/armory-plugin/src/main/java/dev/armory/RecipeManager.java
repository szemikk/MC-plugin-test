package dev.armory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/** Registers one shaped crafting recipe per weapon. */
public final class RecipeManager {

    private final ArmoryPlugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public RecipeManager(ArmoryPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        unregister();
        WeaponManager wm = plugin.weapons();

        for (WeaponType type : wm.all()) {
            NamespacedKey key = new NamespacedKey(plugin, "weapon_" + type.id());
            ShapedRecipe recipe = new ShapedRecipe(key, wm.create(type, 0));
            recipe.shape(type.shape().toArray(new String[0]));

            for (Map.Entry<Character, Material> entry : type.ingredients().entrySet()) {
                boolean used = type.shape().stream().anyMatch(row -> row.indexOf(entry.getKey()) >= 0);
                if (used) {
                    recipe.setIngredient(entry.getKey(), new RecipeChoice.MaterialChoice(entry.getValue()));
                }
            }

            if (Bukkit.addRecipe(recipe)) {
                keys.add(key);
            } else {
                plugin.getLogger().warning("Could not register recipe for " + type.id());
            }
        }
    }

    public void unregister() {
        for (NamespacedKey key : keys) {
            Bukkit.removeRecipe(key);
        }
        keys.clear();
    }

    public List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }
}
