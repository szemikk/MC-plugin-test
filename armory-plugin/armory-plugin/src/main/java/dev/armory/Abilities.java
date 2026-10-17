package dev.armory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Sneak + right-click abilities. Each one scales with the weapon's level. */
public final class Abilities {

    public record Ability(String id, String name, String blurb, BiConsumer<Player, Integer> action) {
    }

    private final ArmoryPlugin plugin;
    private final Map<String, Ability> abilities = new LinkedHashMap<>();
    /** Players whose damage right now comes from an ability (so passives and level bonus are skipped). */
    private final Set<UUID> casting = new HashSet<>();

    public Abilities(ArmoryPlugin plugin) {
        this.plugin = plugin;
        add(new Ability("cinder_burst", "Cinder Burst",
                "Blasts nearby enemies back and sets them on fire.", this::cinderBurst));
        add(new Ability("soul_reap", "Soul Reap",
                "Drags nearby enemies to you, slows them and drains their life.", this::soulReap));
        add(new Ability("phase_step", "Phase Step",
                "Dash forward, cutting through everything in your path.", this::phaseStep));
        add(new Ability("quake_slam", "Quake Slam",
                "Slam the ground and throw nearby enemies into the air.", this::quakeSlam));
    }

    private void add(Ability a) {
        abilities.put(a.id(), a);
    }

    public Ability get(String id) {
        return id == null ? null : abilities.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return abilities.keySet();
    }

    public boolean isCasting(UUID player) {
        return casting.contains(player);
    }

    public void cast(Player player, Ability ability, int level) {
        boolean added = casting.add(player.getUniqueId());
        try {
            ability.action().accept(player, level);
        } finally {
            if (added) {
                casting.remove(player.getUniqueId());
            }
        }
    }

    // ------------------------------------------------------------------ abilities

    private void cinderBurst(Player p, int level) {
        Location center = p.getLocation().add(0, 0.9, 0);
        double radius = 4.5 + 0.5 * level;
        double damage = 5.0 + 1.5 * level;

        for (LivingEntity e : targets(p, center, radius)) {
            hurt(p, e, damage);
            e.setFireTicks(80 + 20 * level);
            push(e, center, 0.7, 0.35);
        }

        World w = center.getWorld();
        Location floor = center.clone().add(0, -0.7, 0);
        ring(floor, radius, Particle.FLAME, 56);
        ring(floor, radius * 0.6, Particle.FLAME, 36);
        w.spawnParticle(Particle.EXPLOSION, center, 1);
        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
    }

    private void soulReap(Player p, int level) {
        Location center = p.getLocation().add(0, 0.9, 0);
        double radius = 7.0 + 0.5 * level;
        double damage = 3.0 + 1.0 * level;
        double healed = 0;

        for (LivingEntity e : targets(p, center, radius)) {
            hurt(p, e, damage);

            Vector pull = p.getLocation().toVector().subtract(e.getLocation().toVector());
            pull.setY(0);
            if (pull.lengthSquared() > 0.01) {
                e.setVelocity(pull.normalize().multiply(0.9).setY(0.25));
            }
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 10 * level, 1));
            e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);

            if (healed < 8.0) {
                heal(p, 1.5);
                healed += 1.5;
            }
        }

        ring(center.clone().add(0, -0.7, 0), radius, Particle.SOUL, 64);
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.4f);
    }

    private void phaseStep(Player p, int level) {
        Vector flat = p.getLocation().getDirection();
        flat.setY(0);
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(0, 0, 1);
        }
        p.setVelocity(flat.normalize().multiply(1.5 + 0.1 * level).setY(0.2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15, 1, true, false));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.6f);

        double damage = 4.0 + 1.25 * level;
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || ticks++ > 8) {
                    cancel();
                    return;
                }
                Location mid = p.getLocation().add(0, 1.0, 0);
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, mid, 1, 0.2, 0.2, 0.2, 0);
                p.getWorld().spawnParticle(Particle.CLOUD, mid, 4, 0.2, 0.3, 0.2, 0.02);
                for (LivingEntity e : targets(p, mid, 1.8)) {
                    if (alreadyHit.add(e.getUniqueId())) {
                        hurt(p, e, damage);
                        push(e, mid, 0.5, 0.25);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void quakeSlam(Player p, int level) {
        Location center = p.getLocation().add(0, 0.9, 0);
        double radius = 5.5 + 0.5 * level;
        double damage = 6.0 + 2.0 * level;

        for (LivingEntity e : targets(p, center, radius)) {
            hurt(p, e, damage);
            e.setVelocity(new Vector(0, 0.75 + 0.05 * level, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 + 10 * level, 2));
        }

        Location floor = center.clone().add(0, -0.7, 0);
        ring(floor, radius, Particle.CLOUD, 64);
        ring(floor, radius * 0.5, Particle.CRIT, 40);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
    }

    // ------------------------------------------------------------------ helpers

    private List<LivingEntity> targets(Player owner, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e.equals(owner) || e instanceof ArmorStand) {
                continue;
            }
            if (le.isDead() || !le.isValid()) {
                continue;
            }
            if (le instanceof Player other
                    && (other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            if (le instanceof Tameable pet && pet.getOwner() != null
                    && pet.getOwner().getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            Location mid = le.getLocation().add(0, le.getHeight() / 2.0, 0);
            if (mid.distanceSquared(center) > radius * radius) {
                continue;
            }
            out.add(le);
        }
        return out;
    }

    private void hurt(Player attacker, LivingEntity victim, double amount) {
        boolean added = casting.add(attacker.getUniqueId());
        try {
            victim.damage(amount, attacker);
        } finally {
            if (added) {
                casting.remove(attacker.getUniqueId());
            }
        }
    }

    private void push(Entity e, Location from, double strength, double lift) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 1.0E-4) {
            v = new Vector(1, 0, 0);
        }
        e.setVelocity(v.normalize().multiply(strength).setY(lift));
    }

    private void ring(Location center, double radius, Particle particle, int points) {
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(particle,
                    center.getX() + Math.cos(a) * radius,
                    center.getY(),
                    center.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0.01);
        }
    }

    public static void heal(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.min(cap, p.getHealth() + amount));
    }
}
