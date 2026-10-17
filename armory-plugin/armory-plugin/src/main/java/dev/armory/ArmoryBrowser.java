package dev.armory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * In-game recipe browser.
 * Page 1 lists every weapon. Clicking one shows its crafting grid and result.
 */
public final class ArmoryBrowser implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Recipe view layout (45 slots, 5 rows)
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW = 23;
    private static final int RESULT = 24;
    private static final int BACK = 40;
    private static final int INFO = 4;

    // List view layout: weapons sit in columns 1-7 of each row after the first
    private static final int PER_ROW = 7;
    private static final int MAX_ROWS = 4;

    private enum View { LIST, RECIPE }

    private static final class Holder implements InventoryHolder {
        final View view;
        Inventory inventory;

        Holder(View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ArmoryPlugin plugin;

    public ArmoryBrowser(ArmoryPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ views

    public void openList(Player player) {
        WeaponManager wm = plugin.weapons();
        List<WeaponType> all = new ArrayList<>(wm.all());
        int shown = Math.min(all.size(), PER_ROW * MAX_ROWS);
        int rows = Math.max(1, (shown + PER_ROW - 1) / PER_ROW) + 2;

        Holder holder = new Holder(View.LIST);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, MM.deserialize("<dark_gray>Armory <gray>» Weapons"));
        holder.inventory = inv;

        for (int i = 0; i < shown; i++) {
            int slot = 9 + (i / PER_ROW) * 9 + 1 + (i % PER_ROW);
            ItemStack icon = wm.create(all.get(i), 0);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(WeaponManager.line("<yellow>Click to view the recipe"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
        }

        inv.setItem(INFO, infoItem());
        fill(inv, false);
        player.openInventory(inv);
    }

    public void openRecipe(Player player, WeaponType type) {
        WeaponManager wm = plugin.weapons();

        Holder holder = new Holder(View.RECIPE);
        Inventory inv = Bukkit.createInventory(holder, 45, MM.deserialize("<dark_gray>Recipe <gray>» ").append(MM.deserialize(type.displayName())));
        holder.inventory = inv;

        List<String> shape = type.shape();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                char ch = (r < shape.size() && c < shape.get(r).length()) ? shape.get(r).charAt(c) : ' ';
                if (ch == ' ') {
                    continue;
                }
                Material m = type.ingredients().get(ch);
                if (m != null) {
                    inv.setItem(GRID[r * 3 + c], new ItemStack(m));
                }
            }
        }

        inv.setItem(ARROW, named(Material.ARROW, "<green>Crafts into"));
        inv.setItem(RESULT, wm.create(type, 0));
        inv.setItem(BACK, named(Material.BARRIER, "<red>Back to all weapons"));
        inv.setItem(INFO, infoItem());
        fill(inv, true);
        player.openInventory(inv);
    }

    private ItemStack infoItem() {
        WeaponManager wm = plugin.weapons();
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Hold a weapon and kill things to upgrade it.");
        lines.add("<gray>Mob kill: <white>+" + wm.pointsForMobKill() + " <gray>| Player kill: <white>+" + wm.pointsForPlayerKill());
        lines.add("");
        List<Integer> th = wm.thresholds();
        for (int level = 1; level < th.size(); level++) {
            lines.add("<gray>Level " + level + ": <white>" + th.get(level) + " points");
        }
        lines.add("");
        lines.add("<gray>Higher levels add damage and shorten the cooldown.");
        lines.add("<gray>Sneak + right-click to use the ability.");
        return named(Material.BOOK, "<gold>How upgrading works", lines.toArray(new String[0]));
    }

    // ------------------------------------------------------------------ helpers

    private void fill(Inventory inv, boolean keepGridEmpty) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        filler.setItemMeta(meta);

        Set<Integer> skip = new HashSet<>();
        if (keepGridEmpty) {
            for (int s : GRID) {
                skip.add(s);
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null && !skip.contains(i)) {
                inv.setItem(i, filler);
            }
        }
    }

    private ItemStack named(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(WeaponManager.line(name));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(WeaponManager.line(l));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }

        int slot = e.getRawSlot();
        if (holder.view == View.LIST) {
            int row = slot / 9;
            int col = slot % 9;
            if (row < 1 || col < 1 || col > PER_ROW) {
                return;
            }
            int index = (row - 1) * PER_ROW + (col - 1);
            List<WeaponType> all = new ArrayList<>(plugin.weapons().all());
            if (index >= 0 && index < all.size() && index < PER_ROW * MAX_ROWS) {
                WeaponType type = all.get(index);
                plugin.getServer().getScheduler().runTask(plugin, () -> openRecipe(player, type));
            }
        } else if (slot == BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openList(player));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
