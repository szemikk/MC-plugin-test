package dev.armory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Handles melee bonuses, passives, abilities and kill-point upgrades. */
public final class WeaponListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ArmoryPlugin plugin;
    /** player -> (weapon id -> time the ability is ready again) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** killer -> (victim -> time of the last rewarded kill) */
    private final Map<UUID, Map<UUID, Long>> recentKills = new HashMap<>();

    public WeaponListener(ArmoryPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ melee

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (plugin.abilities().isCasting(p.getUniqueId())) {
            return;
        }

        WeaponManager wm = plugin.weapons();
        ItemStack hand = p.getInventory().getItemInMainHand();
        WeaponType type = wm.typeOf(hand);
        if (type == null) {
            return;
        }

        int level = wm.levelFor(wm.pointsOf(hand));
        e.setDamage(e.getDamage() * (1.0 + wm.damageBonus() * level));

        Passives.Passive passive = Passives.get(type.passiveId());
        if (passive != null) {
            passive.hit().apply(p, victim, e.getDamage(), level);
        }
    }

    // ------------------------------------------------------------------ upgrades

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || victim instanceof ArmorStand) {
            return;
        }

        WeaponManager wm = plugin.weapons();
        ItemStack hand = killer.getInventory().getItemInMainHand();
        WeaponType type = wm.typeOf(hand);
        if (type == null) {
            return;
        }

        int gain;
        if (victim instanceof Player victimPlayer) {
            gain = wm.pointsForPlayerKill();
            long now = System.currentTimeMillis();
            Map<UUID, Long> seen = recentKills.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
            Long last = seen.get(victimPlayer.getUniqueId());
            if (last != null && now - last < wm.antiFarmSeconds() * 1000L) {
                killer.sendActionBar(MM.deserialize("<gray>No points: you killed that player recently."));
                return;
            }
            if (gain > 0) {
                seen.put(victimPlayer.getUniqueId(), now);
            }
        } else {
            gain = wm.pointsForMobKill();
        }
        if (gain <= 0) {
            return;
        }

        int before = wm.pointsOf(hand);
        int oldLevel = wm.levelFor(before);
        int after = before + gain;

        wm.setPoints(hand, after);
        killer.getInventory().setItemInMainHand(hand);

        int newLevel = wm.levelFor(after);
        if (newLevel > oldLevel) {
            levelUp(killer, type, newLevel);
        } else {
            killer.sendActionBar(MM.deserialize("<gray>+" + gain + " kill point" + (gain == 1 ? "" : "s")));
        }
    }

    private void levelUp(Player p, WeaponType type, int level) {
        p.showTitle(Title.title(
                MM.deserialize(type.displayName()),
                MM.deserialize("<yellow>reached level " + level),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
    }

    // ------------------------------------------------------------------ abilities

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.isSneaking() || e.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }

        WeaponManager wm = plugin.weapons();
        ItemStack hand = p.getInventory().getItemInMainHand();
        WeaponType type = wm.typeOf(hand);
        if (type == null) {
            return;
        }
        e.setCancelled(true);

        if (!p.hasPermission("armory.use")) {
            p.sendActionBar(MM.deserialize("<red>You can't use weapon abilities."));
            return;
        }
        Abilities.Ability ability = plugin.abilities().get(type.abilityId());
        if (ability == null) {
            return;
        }

        int level = wm.levelFor(wm.pointsOf(hand));
        long now = System.currentTimeMillis();
        Map<String, Long> mine = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long readyAt = mine.get(type.id());
        if (readyAt != null && readyAt > now) {
            double left = (readyAt - now) / 1000.0;
            p.sendActionBar(MM.deserialize("<red>" + ability.name() + " recharging: "
                    + String.format("%.1f", left) + "s"));
            return;
        }

        mine.put(type.id(), now + (long) (wm.cooldownSeconds(type, level) * 1000));
        plugin.abilities().cast(p, ability, level);
        p.sendActionBar(MM.deserialize("<gold>" + ability.name() + " <gray>unleashed"));
    }

    // ------------------------------------------------------------------ housekeeping

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.weapons().discoverOnJoin()) {
            e.getPlayer().discoverRecipes(plugin.recipes().keys());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
        recentKills.remove(e.getPlayer().getUniqueId());
    }
}
