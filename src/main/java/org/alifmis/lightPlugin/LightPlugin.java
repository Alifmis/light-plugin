package org.alifmis.lightPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inv)) return;
        if (event.getView().getType() != InventoryType.WORKBENCH) return;

        ItemStack[] matrix = inv.getMatrix();

        // Check center slot: exactly 1 glass block
        ItemStack center = matrix[CENTER_SLOT];
        if (center == null || center.getType() != Material.GLASS || center.getAmount() != 1) {
            inv.setResult(null);
            return;
        }

        int torchCount = 0;

        for (int slot : SURROUNDING) {
            ItemStack item = matrix[slot];

            if (item == null || item.getType() == Material.AIR) {
                continue; // empty slot — allowed
            }

            if (item.getType() == Material.TORCH && item.getAmount() == 1) {
                torchCount++;
            } else {
                // wrong item in a surrounding slot
                inv.setResult(null);
                return;
            }
        }

        if (torchCount < 1 || torchCount > 8) {
            inv.setResult(null);
            return;
        }

        // Light level = 2n - 1  (values: 1, 3, 5, 7, 9, 11, 13, 15)
        int lightLevel = 2 * torchCount - 1;

        ItemStack result = new ItemStack(Material.LIGHT);

        try {
            Light lightData = (Light) Bukkit.createBlockData(Material.LIGHT);
            lightData.setLevel(lightLevel);

            // Use setItemMeta for broadest compatibility with Paper 1.21.11
            BlockDataMeta meta = (BlockDataMeta) result.getItemMeta();
            if (meta != null) {
                meta.setBlockData(lightData);
                result.setItemMeta(meta);
            }
        } catch (Exception e) {
            getLogger().warning("Failed to set light level " + lightLevel + ": " + e.getMessage());
        }

        inv.setResult(result);
    }
}
