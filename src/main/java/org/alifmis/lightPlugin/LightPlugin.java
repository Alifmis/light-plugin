package org.alifmis.lightPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class LightPlugin extends JavaPlugin implements Listener {

    // 3x3 matrix: slot 4 is the center glass; slots 0-3 and 5-8 are surrounding (torch or empty)
    private static final int CENTER_SLOT = 4;
    private static final int[] SURROUNDING = {0, 1, 2, 3, 5, 6, 7, 8};

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        registerRecipes();
        getLogger().info("LightPlugin enabled — craft Light blocks from Glass + Torches");
    }

    @Override
    public void onDisable() {
        getLogger().info("LightPlugin disabled");
    }

    private void registerRecipes() {
        for (int torchCount = 1; torchCount <= 8; torchCount++) {
            NamespacedKey key = new NamespacedKey(this, "light_" + torchCount);
            ItemStack result = makeLightItem(torchCount);
            ShapelessRecipe recipe = new ShapelessRecipe(key, result);
            recipe.addIngredient(1, Material.GLASS);
            recipe.addIngredient(torchCount, Material.TORCH);
            Bukkit.addRecipe(recipe);
        }
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

    // True if the matrix contains any glass or torch, a potential light recipe attempt
    private boolean hasGlassOrTorch(ItemStack[] matrix) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = matrix[i];
            if (item != null && (item.getType() == Material.GLASS || item.getType() == Material.TORCH)) {
                return true;
            }
        }
        return false;
    }

    // Torch count if the matrix is a valid light recipe (glass centered, torches around), else -1
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
        InventoryType type = event.getView().getType();
        if (type != InventoryType.WORKBENCH && type != InventoryType.CRAFTER) return;

        // Only intervene when the grid contains glass or a torch; otherwise vanilla computes the result
        ItemStack[] matrix = inv.getMatrix();
        if (!hasGlassOrTorch(matrix)) return;

        int torchCount = validateMatrix(matrix);
        if (torchCount < 1) {
            inv.setResult(null);
            return;
        }

        inv.setResult(makeLightItem(torchCount));
    }

    // Crafter auto-craft: validate the matrix and set the result to the matching light level
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCrafterCraft(CrafterCraftEvent event) {
        BlockState state = event.getBlock().getState();
        if (!(state instanceof InventoryHolder holder)) return;

        Inventory inv = holder.getInventory();
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = inv.getItem(i);
        }

        // Skip crafts with no glass or torches so other plugins can handle them
        if (!hasGlassOrTorch(matrix)) return;

        int torchCount = validateMatrix(matrix);
        if (torchCount < 1) {
            event.setCancelled(true);
            return;
        }

        event.setResult(makeLightItem(torchCount));
    }

    // LOWEST priority: runs before CraftItemEvent and blocks the server's own craft processing
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inv)) return;
        if (event.getView().getType() != InventoryType.WORKBENCH) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.LIGHT) return;

        // Cancel before CraftItemEvent so no handler sees the event as allowed
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
        if (maxCrafts < 1) return;

        Player player = (Player) event.getWhoClicked();

        if (event.isShiftClick()) {
            // Shift-click: craft as many as fit into the inventory, leave the excess in the grid
            doShiftCraft(inv, matrix, torchCount, maxCrafts, player);
        } else {
            // Left-click or right-click: craft 1 to cursor.
            doSingleCraft(inv, matrix, torchCount, event, player);
        }

        player.updateInventory();
    }

    private void doShiftCraft(CraftingInventory inv, ItemStack[] matrix, int torchCount, int maxCrafts, Player player) {
        ItemStack resultStack = makeLightItem(torchCount);
        resultStack.setAmount(maxCrafts);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(resultStack);
        int actuallyGiven = maxCrafts;
        for (ItemStack rem : leftover.values()) {
            actuallyGiven -= rem.getAmount();
        }
        if (actuallyGiven < 1) return;

        consumeIngredients(inv, matrix, actuallyGiven);
    }

    private void doSingleCraft(CraftingInventory inv, ItemStack[] matrix, int torchCount, InventoryClickEvent event, Player player) {
        ItemStack oneCraft = makeLightItem(torchCount);
        oneCraft.setAmount(1);

        ItemStack cursor = event.getCursor();

        if (cursor == null || cursor.getType() == Material.AIR) {
            // Empty cursor — put result on cursor
            event.setCursor(oneCraft);
        } else if (cursor.isSimilar(oneCraft) && cursor.getAmount() < cursor.getMaxStackSize()) {
            // Stack with cursor
            cursor.setAmount(cursor.getAmount() + 1);
        } else {
            // Different or full cursor: add to inventory, drop at feet if it does not fit
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(oneCraft);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
        }

        consumeIngredients(inv, matrix, 1);
    }

    // Use inv.setItem() (not setMatrix()) to avoid the n->(n-1)*2 doubling bug
    private void consumeIngredients(CraftingInventory inv, ItemStack[] matrix, int crafts) {
        // Consume glass (center, slot index 4)
        ItemStack center = matrix[CENTER_SLOT];
        int glassLeft = center.getAmount() - crafts;
        if (glassLeft <= 0) {
            inv.setItem(1 + CENTER_SLOT, null);
        } else {
            inv.setItem(1 + CENTER_SLOT, new ItemStack(Material.GLASS, glassLeft));
        }

        // Consume torches (surrounding slots)
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
    }
}
