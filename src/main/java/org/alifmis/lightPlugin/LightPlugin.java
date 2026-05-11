package org.alifmis.lightPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class LightPlugin extends JavaPlugin implements Listener {

    private static final int CENTER_SLOT = 4;
    private static final int[] SURROUNDING = {0, 1, 2, 3, 5, 6, 7, 8};

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("LightPlugin enabled — craft Light blocks from Glass + Torches");
    }

    @Override
    public void onDisable() {
        getLogger().info("LightPlugin disabled");
    }

    private ItemStack makeLightItem(int torchCount) {
        int lightLevel = 2 * torchCount - 1;
        ItemStack result = new ItemStack(Material.LIGHT);
        try {
            Light lightData = (Light) Bukkit.createBlockData(Material.LIGHT);
            lightData.setLevel(lightLevel);
            BlockDataMeta meta = (BlockDataMeta) result.getItemMeta();
            if (meta != null) {
                meta.setBlockData(lightData);
                result.setItemMeta(meta);
            }
        } catch (Exception e) {
            getLogger().warning("Failed to set light level " + lightLevel + ": " + e.getMessage());
        }
        return result;
    }

    private int validateMatrix(ItemStack[] matrix) {
        ItemStack center = matrix[CENTER_SLOT];
        if (center == null || center.getType() != Material.GLASS || center.getAmount() < 1) {
            return -1;
        }

        int torchCount = 0;
        for (int slot : SURROUNDING) {
            ItemStack item = matrix[slot];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == Material.TORCH && item.getAmount() >= 1) {
                torchCount++;
            } else {
                return -1;
            }
        }

        if (torchCount < 1 || torchCount > 8) {
            return -1;
        }
        return torchCount;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inv)) return;
        if (event.getView().getType() != InventoryType.WORKBENCH) return;

        int torchCount = validateMatrix(inv.getMatrix());
        if (torchCount < 1) {
            inv.setResult(null);
            return;
        }

        inv.setResult(makeLightItem(torchCount));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inv)) return;
        if (event.getView().getType() != InventoryType.WORKBENCH) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.isRightClick()) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.LIGHT) return;

        event.setCancelled(true);

        ItemStack[] matrix = inv.getMatrix();
        int torchCount = validateMatrix(matrix);
        if (torchCount < 1) return;

        // Calculate max possible crafts
        ItemStack center = matrix[CENTER_SLOT];
        int maxCrafts = center.getAmount();
        for (int slot : SURROUNDING) {
            ItemStack item = matrix[slot];
            if (item != null && item.getType() == Material.TORCH) {
                maxCrafts = Math.min(maxCrafts, item.getAmount());
            }
        }

        int crafts = event.isShiftClick() ? maxCrafts : 1;
        if (crafts < 1) return;

        // Consume glass
        int glassLeft = center.getAmount() - crafts;
        if (glassLeft <= 0) {
            inv.setItem(1 + CENTER_SLOT, null);
        } else {
            inv.setItem(1 + CENTER_SLOT, new ItemStack(Material.GLASS, glassLeft));
        }

        // Consume torches
        for (int slot : SURROUNDING) {
            ItemStack item = matrix[slot];
            if (item == null || item.getType() == Material.AIR) continue;
            int torchLeft = item.getAmount() - crafts;
            if (torchLeft <= 0) {
                inv.setItem(1 + slot, null);
            } else {
                inv.setItem(1 + slot, new ItemStack(Material.TORCH, torchLeft));
            }
        }

        // Give result to player
        Player player = (Player) event.getWhoClicked();
        ItemStack crafted = makeLightItem(torchCount);
        crafted.setAmount(crafts);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(crafted);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), drop);
        }

        // Force the crafting slot to recalculate the preview
        player.updateInventory();
    }
}
