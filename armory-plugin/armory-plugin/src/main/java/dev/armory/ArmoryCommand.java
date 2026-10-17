package dev.armory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ArmoryCommand implements TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ArmoryPlugin plugin;

    public ArmoryCommand(ArmoryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "browser" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "browser", "browse", "recipes" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can open the browser.");
                } else if (!player.hasPermission("armory.browse")) {
                    msg(sender, "<red>You don't have permission.");
                } else {
                    plugin.browser().openList(player);
                }
            }
            case "list" -> {
                msg(sender, "<gold>Weapons: <white>" + String.join(", ",
                        plugin.weapons().all().stream().map(WeaponType::id).toList()));
            }
            case "give" -> give(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("armory.admin")) {
                    msg(sender, "<red>You don't have permission.");
                } else {
                    plugin.reload();
                    msg(sender, "<green>Armory reloaded: " + plugin.weapons().all().size() + " weapons.");
                }
            }
            default -> msg(sender, "<gray>Usage: <white>/armory [browser|list|give <player> <weapon> [points]|reload]");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("armory.admin")) {
            msg(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 3) {
            msg(sender, "<gray>Usage: <white>/armory give <player> <weapon> [points]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>Player not found.");
            return;
        }
        WeaponType type = plugin.weapons().get(args[2]);
        if (type == null) {
            msg(sender, "<red>Unknown weapon. Try /armory list");
            return;
        }
        int points = 0;
        if (args.length > 3) {
            try {
                points = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                msg(sender, "<red>Points must be a number.");
                return;
            }
        }
        ItemStack item = plugin.weapons().create(type, points);
        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        msg(sender, "<green>Gave " + type.id() + " to " + target.getName() + ".");
    }

    private void msg(CommandSender sender, String miniMessage) {
        sender.sendMessage(MM.deserialize(miniMessage));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("browser", "list"));
            if (sender.hasPermission("armory.admin")) {
                out.addAll(List.of("give", "reload"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("armory.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("armory.admin")) {
            plugin.weapons().all().forEach(t -> out.add(t.id()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
