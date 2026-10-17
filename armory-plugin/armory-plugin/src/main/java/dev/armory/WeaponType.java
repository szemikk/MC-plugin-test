package dev.armory;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/** One weapon definition, loaded from config.yml. */
public record WeaponType(
        String id,
        String displayName,
        Material material,
        String abilityId,
        String passiveId,
        double baseCooldown,
        List<String> description,
        List<String> shape,
        Map<Character, Material> ingredients) {
}
