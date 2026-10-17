package dev.armory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Loads weapon definitions and builds / reads / refreshes weapon items. */
public final class WeaponManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ArmoryPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey pointsKey;
    private final Map<String, WeaponType> weapons = new LinkedHashMap<>();

    private List<Integer> thresholds = List.of(0);
    private double damageBonus;
    private double cooldownReduction;
    private int playerKillPoints;
    private int mobKillPoints;
    private int antiFarmSeconds;
    private boolean discoverOnJoin;
    private boolean unbreakable;

    public WeaponManager(ArmoryPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "weapon_id");
        this.pointsKey = new NamespacedKey(plugin, "weapon_points");
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        FileConfiguration cfg = plugin.getConfig();
        weapons.clear();

        List<Integer> th = new ArrayList<>(cfg.getIntegerList("settings.level-thresholds"));
        if (th.isEmpty() || th.get(0) != 0) {
            th.add(0, 0);
        }
        Collections.sort(th);
        thresholds = th;

        damageBonus = cfg.getDouble("settings.damage-bonus-per-level", 0.08);
        cooldownReduction = cfg.getDouble("settings.cooldown-reduction-per-level", 0.06);
        playerKillPoints = cfg.getInt("settings.kill-points.player", 3);
        mobKillPoints = cfg.getInt("settings.kill-points.mob", 1);
        antiFarmSeconds = cfg.getInt("settings.anti-farm-seconds", 120);
        unbreakable = cfg.getBoolean("settings.unbreakable", true);
        discoverOnJoin = cfg.getBoolean("settings.discover-recipes-on-join", true);

        ConfigurationSection section = cfg.getConfigurationSection("weapons");
        if (section == null) {
            plugin.getLogger().warning("No weapons defined in config.yml");
            return;
        }

        for (String rawId : section.getKeys(false)) {
            ConfigurationSection w = section.getConfigurationSection(rawId);
            if (w == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);

            Material material = Material.matchMaterial(w.getString("material", ""));
            if (material == null || !material.isItem()) {
                warn(id, "unknown material");
                continue;
            }
            String ability = w.getString("ability", "");
            if (plugin.abilities().get(ability) == null) {
                warn(id, "unknown ability '" + ability + "'");
                continue;
            }
            String passive = w.getString("passive", "");
            if (Passives.get(passive) == null) {
                warn(id, "unknown passive '" + passive + "'");
                continue;
            }

            List<String> rows = new ArrayList<>(w.getStringList("recipe.shape"));
            if (rows.isEmpty() || rows.size() > 3) {
                warn(id, "recipe.shape needs 1-3 rows");
                continue;
            }
            int width = rows.stream().mapToInt(String::length).max().orElse(0);
            if (width < 1 || width > 3) {
                warn(id, "recipe rows must be 1-3 characters wide");
                continue;
            }
            List<String> shape = new ArrayList<>();
            for (String row : rows) {
                shape.add(String.format("%-" + width + "s", row));
            }

            Map<Character, Material> ingredients = new HashMap<>();
            ConfigurationSection ing = w.getConfigurationSection("recipe.ingredients");
            if (ing != null) {
                for (String key : ing.getKeys(false)) {
                    Material m = Material.matchMaterial(ing.getString(key, ""));
                    if (key.length() == 1 && m != null) {
                        ingredients.put(key.charAt(0), m);
                    } else {
                        warn(id, "bad ingredient '" + key + "'");
                    }
                }
            }

            boolean valid = false;
            boolean missing = false;
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c == ' ') {
                        continue;
                    }
                    valid = true;
                    if (!ingredients.containsKey(c)) {
                        missing = true;
                    }
                }
            }
            if (!valid || missing) {
                warn(id, "recipe is empty or uses a letter with no ingredient");
                continue;
            }

            weapons.put(id, new WeaponType(
                    id,
                    w.getString("name", id),
                    material,
                    ability.toLowerCase(Locale.ROOT),
                    passive.toLowerCase(Locale.ROOT),
                    Math.max(1.0, w.getDouble("base-cooldown", 12)),
                    w.getStringList("description"),
                    shape,
                    ingredients));
        }
    }

    private void warn(String id, String message) {
        plugin.getLogger().warning("Skipping weapon '" + id + "': " + message);
    }

    // ------------------------------------------------------------------ lookups

    public Collection<WeaponType> all() {
        return weapons.values();
    }

    public WeaponType get(String id) {
        return id == null ? null : weapons.get(id.toLowerCase(Locale.ROOT));
    }

    public WeaponType typeOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? null : weapons.get(id);
    }

    public int pointsOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer p = item.getItemMeta().getPersistentDataContainer().get(pointsKey, PersistentDataType.INTEGER);
        return p == null ? 0 : p;
    }

    public List<Integer> thresholds() {
        return thresholds;
    }

    public int maxLevel() {
        return thresholds.size() - 1;
    }

    public int levelFor(int points) {
        for (int i = thresholds.size() - 1; i >= 0; i--) {
            if (points >= thresholds.get(i)) {
                return i;
            }
        }
        return 0;
    }

    public double damageBonus() {
        return damageBonus;
    }

    public int pointsForPlayerKill() {
        return playerKillPoints;
    }

    public int pointsForMobKill() {
        return mobKillPoints;
    }

    public int antiFarmSeconds() {
        return antiFarmSeconds;
    }

    public boolean discoverOnJoin() {
        return discoverOnJoin;
    }

    public double cooldownSeconds(WeaponType type, int level) {
        double base = type.baseCooldown();
        return Math.max(base * 0.4, base * (1.0 - cooldownReduction * level));
    }

    // ------------------------------------------------------------------ items

    public ItemStack create(WeaponType type, int points) {
        ItemStack item = new ItemStack(type.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(pointsKey, PersistentDataType.INTEGER, Math.max(0, points));
        if (unbreakable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        item.setItemMeta(meta);
        refresh(item);
        return item;
    }

    public void setPoints(ItemStack item, int points) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(pointsKey, PersistentDataType.INTEGER, Math.max(0, points));
        item.setItemMeta(meta);
        refresh(item);
    }

    /** Rewrites the name and lore from the item's stored points. */
    public void refresh(ItemStack item) {
        WeaponType type = typeOf(item);
        if (type == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        int points = pointsOf(item);
        int level = levelFor(points);
        int max = maxLevel();

        meta.displayName(line(type.displayName() + " <dark_gray>[<yellow>Lv " + level + "<dark_gray>]"));

        List<Component> lore = new ArrayList<>();

        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= max; i++) {
            stars.append(i <= level ? "<yellow>★" : "<dark_gray>☆");
        }
        lore.add(line("<gray>Rank: " + stars));

        if (level < max) {
            lore.add(line("<gray>Kill points: <white>" + points + "<dark_gray>/<white>" + thresholds.get(level + 1)));
        } else {
            lore.add(line("<gray>Kill points: <white>" + points + " <gold>(MAX)"));
        }
        if (level > 0) {
            long bonus = Math.round(damageBonus * level * 100.0);
            lore.add(line("<gray>Damage bonus: <green>+" + bonus + "%"));
        }

        lore.add(Component.empty());
        for (String d : type.description()) {
            lore.add(line("<gray>" + d));
        }
        lore.add(Component.empty());

        Abilities.Ability ability = plugin.abilities().get(type.abilityId());
        if (ability != null) {
            lore.add(line("<gold>Ability: <white>" + ability.name() + " <dark_gray>(Sneak + Right-click)"));
            lore.add(line("<gray>" + ability.blurb()));
            lore.add(line("<gray>Cooldown: <white>" + String.format(Locale.ROOT, "%.1f", cooldownSeconds(type, level)) + "s"));
        }
        Passives.Passive passive = Passives.get(type.passiveId());
        if (passive != null) {
            lore.add(line("<gold>Passive: <white>" + passive.name()));
            lore.add(line("<gray>" + passive.blurb()));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public static Component line(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
