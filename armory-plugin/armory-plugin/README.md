# Armory

A Paper plugin (1.21.4+, Java 21) that adds upgradeable custom weapons.

## What it does

- **Four original weapons**, each with a sneak + right-click ability and a passive on-hit effect:
  - Emberglass Cleaver (netherite axe): Cinder Burst + Searing Edge
  - Gravewhisper Scythe (diamond hoe): Soul Reap + Hungering Blade
  - Zephyr Needle (diamond sword): Phase Step + Tailwind
  - Quakebreaker Maul (mace): Quake Slam + Rattling Blows
- **Kill-based upgrades.** Holding a weapon while you kill things earns points
  (mobs +1, players +3 by default). Points are stored on the item itself, so they
  survive dropping, trading and chests. More levels = more damage and shorter ability cooldown.
- **Anti-farming.** Killing the same player again within 120 s gives no points.
- **Custom recipe browser.** `/armory` opens a GUI listing every weapon; clicking one
  shows its crafting grid and result. Recipes also appear in the vanilla recipe book.

## Build

```
mvn package
```

The jar is written to `target/Armory-1.0.0.jar`. Drop it in your server's `plugins/` folder.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/armory` (or `/weapons`, `/recipes`) | Open the recipe browser | `armory.browse` (everyone) |
| `/armory list` | List weapon ids | none |
| `/armory give <player> <weapon> [points]` | Give a weapon, optionally pre-upgraded | `armory.admin` (op) |
| `/armory reload` | Reload `config.yml` and re-register recipes | `armory.admin` |

## Customising

Everything is in `config.yml`: level thresholds, damage and cooldown scaling, kill points,
and every weapon's name, base item, recipe, ability and passive.

To add a weapon, copy a block under `weapons:`, give it a new id, and pick:

- `ability`: `cinder_burst`, `soul_reap`, `phase_step`, `quake_slam`
- `passive`: `ignite`, `lifesteal`, `momentum`, `concuss`

To add a brand-new ability or passive, register it in `Abilities.java` or `Passives.java`
(one method each) and it becomes available in the config.

## Notes

- Weapon abilities only trigger when sneaking, so normal right-click behaviour is unchanged.
- Ability damage does not trigger passives or the level damage bonus, so abilities can't chain into themselves.
- Weapons are unbreakable by default (`settings.unbreakable`).
