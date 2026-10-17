package dev.armory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Effects that trigger on every melee hit with a weapon. */
public final class Passives {

    @FunctionalInterface
    public interface Hit {
        void apply(Player attacker, LivingEntity victim, double damage, int level);
    }

    public record Passive(String id, String name, String blurb, Hit hit) {
    }

    private static final Map<String, Passive> PASSIVES = new LinkedHashMap<>();

    static {
        add(new Passive("ignite", "Searing Edge",
                "Hits set the target ablaze.",
                (p, v, d, l) -> v.setFireTicks(40 + 20 * l)));

        add(new Passive("lifesteal", "Hungering Blade",
                "Heals you for a share of the damage you deal.",
                (p, v, d, l) -> Abilities.heal(p, d * (0.06 + 0.02 * l))));

        add(new Passive("momentum", "Tailwind",
                "Each hit grants a short burst of speed.",
                (p, v, d, l) -> p.addPotionEffect(
                        new PotionEffect(PotionEffectType.SPEED, 60 + 20 * l, 0, true, false))));

        add(new Passive("concuss", "Rattling Blows",
                "Hits slow and weaken the target.",
                (p, v, d, l) -> {
                    v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 + 10 * l, 0));
                    v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40 + 10 * l, 0));
                }));
    }

    private Passives() {
    }

    private static void add(Passive p) {
        PASSIVES.put(p.id(), p);
    }

    public static Passive get(String id) {
        return id == null ? null : PASSIVES.get(id.toLowerCase(Locale.ROOT));
    }

    public static Iterable<String> ids() {
        return PASSIVES.keySet();
    }
}
