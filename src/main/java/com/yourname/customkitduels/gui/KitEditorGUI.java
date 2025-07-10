package com.yourname.customkitduels.gui;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.Kit;
import com.yourname.customkitduels.utils.FontUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KitEditorGUI implements Listener {
    
    private final CustomKitDuels plugin;
    private final Player player;
    private final String kitName;
    private final boolean isNewKit;
    private final Inventory gui;
    private final ItemStack[] kitContents;
    private final ItemStack[] kitArmor;
    private ItemStack offhandItem;
    private static final Map<UUID, KitEditorGUI> activeGuis = new HashMap<>();
    private boolean isActive = true;
    private boolean isBulkMode = false;
    private ItemStack bulkItem = null;
    private boolean isNavigating = false;
    
    public KitEditorGUI(CustomKitDuels plugin, Player player, String kitName, boolean isNewKit) {
        this.plugin = plugin;
        this.player = player;
        this.kitName = kitName;
        this.isNewKit = isNewKit;
        this.gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + FontUtils.toSmallCaps("editing ") + kitName);
        this.kitContents = new ItemStack[36];
        this.kitArmor = new ItemStack[4];
        this.offhandItem = null;
        
        // Load existing kit if editing
        if (!isNewKit) {
            Kit existingKit = plugin.getKitManager().getKit(player.getUniqueId(), kitName);
            if (existingKit != null) {
                System.arraycopy(existingKit.getContents(), 0, kitContents, 0, Math.min(existingKit.getContents().length, 36));
                System.arraycopy(existingKit.getArmor(), 0, kitArmor, 0, 4);
                // Load offhand if available (stored in slot 36 of contents array)
                if (existingKit.getContents().length > 36 && existingKit.getContents()[36] != null) {
                    this.offhandItem = existingKit.getContents()[36];
                }
            }
        }
        
        setupGUI();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void setupGUI() {
        // Clear GUI first
        gui.clear();
        
        // Fill main inventory slots (0-35) with colored glass panes or items
        for (int i = 0; i < 36; i++) {
            updateSlot(i);
        }
        
        // Fill armor slots (36-39) with colored glass panes or items
        for (int i = 0; i < 4; i++) {
            updateArmorSlot(i);
        }
        
        // Add offhand slot (slot 40)
        updateOffhandSlot();
        
        // Add control buttons
        setupControlButtons();
        
    }
    
    private void updateSlot(int slot) {
        if (kitContents[slot] != null) {
            gui.setItem(slot, kitContents[slot].clone());
        } else {
            ItemStack glassPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = glassPane.getItemMeta();
            meta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps("slot #") + (slot + 1));
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("click to add item"),
                ChatColor.YELLOW + FontUtils.toSmallCaps("right-click to modify item")
            ));
            glassPane.setItemMeta(meta);
            gui.setItem(slot, glassPane);
        }
    }
    
    private void updateArmorSlot(int armorIndex) {
        String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
        int guiSlot = 36 + armorIndex;
        
        if (kitArmor[armorIndex] != null) {
            gui.setItem(guiSlot, kitArmor[armorIndex].clone());
        } else {
            ItemStack glassPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = glassPane.getItemMeta();
            meta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps(armorSlots[armorIndex] + " slot"));
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("click to add armor"),
                ChatColor.YELLOW + FontUtils.toSmallCaps("right-click to modify armor")
            ));
            glassPane.setItemMeta(meta);
            gui.setItem(guiSlot, glassPane);
        }
    }
    
    private void updateOffhandSlot() {
        if (offhandItem != null) {
            gui.setItem(40, offhandItem.clone());
        } else {
            ItemStack offhandPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta offhandMeta = offhandPane.getItemMeta();
            offhandMeta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps("offhand slot"));
            offhandMeta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("click to add item"),
                ChatColor.YELLOW + FontUtils.toSmallCaps("right-click to modify item")
            ));
            offhandPane.setItemMeta(offhandMeta);
            gui.setItem(40, offhandPane);
        }
    }
    
    private void setupControlButtons() {
        ItemStack saveButton = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = saveButton.getItemMeta();
        saveMeta.setDisplayName(ChatColor.GREEN + FontUtils.toSmallCaps("save kit"));
        saveMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("save your kit")));
        saveButton.setItemMeta(saveMeta);
        gui.setItem(45, saveButton);
        
        ItemStack cancelButton = new ItemStack(Material.REDSTONE);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps("cancel"));
        cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("close without saving")));
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(53, cancelButton);
        
        // Clear button
        ItemStack clearButton = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName(ChatColor.YELLOW + FontUtils.toSmallCaps("clear all"));
        clearMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("remove all items")));
        clearButton.setItemMeta(clearMeta);
        gui.setItem(49, clearButton);
        
        // Kit Settings button
        ItemStack settingsButton = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settingsButton.getItemMeta();
        settingsMeta.setDisplayName(ChatColor.LIGHT_PURPLE + FontUtils.toSmallCaps("kit settings"));
        settingsMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("configure kit options")));
        settingsButton.setItemMeta(settingsMeta);
        gui.setItem(46, settingsButton);
        
        // ENHANCED BULK MODE BUTTON
        ItemStack bulkButton = new ItemStack(isBulkMode ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta bulkMeta = bulkButton.getItemMeta();
        
        if (isBulkMode) {
            bulkMeta.setDisplayName(ChatColor.GREEN + "🔥 " + FontUtils.toSmallCaps("bulk mode: active"));
            bulkMeta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("bulk mode is currently active"),
                ChatColor.YELLOW + FontUtils.toSmallCaps("item: ") + (bulkItem != null ? bulkItem.getType().name() : FontUtils.toSmallCaps("none")),
                ChatColor.AQUA + FontUtils.toSmallCaps("click slots to place this item"),
                ChatColor.RED + FontUtils.toSmallCaps("right-click to exit bulk mode"),
                ChatColor.GOLD + FontUtils.toSmallCaps("left-click to change bulk item")
            ));
        } else {
            bulkMeta.setDisplayName(ChatColor.YELLOW + "🔥 " + FontUtils.toSmallCaps("bulk mode"));
            bulkMeta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("activate bulk mode to quickly"),
                ChatColor.GRAY + FontUtils.toSmallCaps("place the same item in multiple slots"),
                ChatColor.GREEN + FontUtils.toSmallCaps("click to select bulk item"),
                ChatColor.AQUA + FontUtils.toSmallCaps("or shift-click any slot with an item")
            ));
        }
        
        bulkButton.setItemMeta(bulkMeta);
        gui.setItem(47, bulkButton);
    }
    
    public void open() {
        // Clean up any existing GUI for this player
        KitEditorGUI existing = activeGuis.get(player.getUniqueId());
        if (existing != null && existing != this) {
            existing.forceCleanup();
        }
        
        activeGuis.put(player.getUniqueId(), this);
        isActive = true;
        isNavigating = false;
        player.openInventory(gui);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        
        // CRITICAL: Only handle events for our specific player and GUI
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(gui)) return;
        if (!isActive || isNavigating) return;
        
        event.setCancelled(true);
        int slot = event.getSlot();
        ClickType clickType = event.getClick();
        
        // Handle enhanced bulk mode button
        if (slot == 47) {
            if (isBulkMode) {
                if (clickType == ClickType.RIGHT) {
                    exitBulkMode();
                    return;
                } else if (clickType == ClickType.LEFT) {
                    // Change bulk item
                    openCategorySelectorForBulk(0);
                    return;
                }
            } else {
                // Activate bulk mode
                openCategorySelectorForBulk(0);
                return;
            }
        }
        
        // Handle control buttons
        if (slot == 45) { // Save button
            saveKit();
            return;
        }
        
        if (slot == 53) { // Cancel button
            forceCleanup();
            return;
        }
        
        if (slot == 49) { // Clear button
            clearAllSlots();
            return;
        }
        
        if (slot == 46) { // Kit Settings button
            openKitSettings();
            return;
        }
        
        // Handle slot selection (0-40: main inventory, armor, and offhand)
        if (slot <= 40) {
            // Handle bulk mode
            if (isBulkMode && bulkItem != null) {
                if (slot < 36) { // Only main inventory slots for bulk mode
                    setSlotItem(slot, bulkItem.clone());
                    player.sendMessage(ChatColor.GREEN + "🔥 " + FontUtils.toSmallCaps("bulk placed ") + bulkItem.getType().name() + FontUtils.toSmallCaps(" in slot ") + (slot + 1) + "!");
                    return;
                } else {
                    player.sendMessage(ChatColor.RED + FontUtils.toSmallCaps("bulk mode only works for main inventory slots!"));
                    return;
                }
            }
            
            // Handle shift-click for bulk mode activation
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                ItemStack currentItem = getCurrentItemInSlot(slot);
                if (currentItem != null && !isPlaceholderItem(currentItem)) {
                    enterBulkMode(currentItem);
                    return;
                } else if (slot < 36) { // Only for main inventory slots
                    // Open category selector to choose bulk item
                    openCategorySelectorForBulk(slot);
                    return;
                }
            }
            
            if (clickType == ClickType.RIGHT) {
                // Right-click: open item modification menu if item exists
                ItemStack currentItem = getCurrentItemInSlot(slot);
                if (currentItem != null && !isPlaceholderItem(currentItem)) {
                    plugin.getLogger().info("[DEBUG] Right-click on slot " + slot + " with item " + currentItem.getType());
                    openItemModificationMenu(slot, currentItem);
                    return;
                }
            }
            
            // Left-click or right-click on empty slot: open appropriate selector
            // Determine what type of selector to open based on slot
            if (slot >= 36 && slot <= 39) {
                // Armor slot - open armor-specific selector
                openArmorSelector(slot);
            } else {
                // Regular slot - open category selector
                openCategorySelector(slot);
            }
        }
    }
    
    private void enterBulkMode(ItemStack item) {
        isBulkMode = true;
        bulkItem = item.clone();
        setupControlButtons(); // Refresh to show bulk indicator
        player.sendMessage(ChatColor.GREEN + "🔥 " + FontUtils.toSmallCaps("bulk mode activated! click slots to place ") + item.getType().name());
        player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("right-click the green button to exit bulk mode"));
    }
    
    private void exitBulkMode() {
        isBulkMode = false;
        bulkItem = null;
        setupControlButtons(); // Refresh to hide bulk indicator
        player.sendMessage(ChatColor.YELLOW + "🔥 " + FontUtils.toSmallCaps("bulk mode deactivated"));
    }
    
    private void openCategorySelectorForBulk(int slot) {
        // Set navigation state to prevent cleanup
        isNavigating = true;
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new BulkCategorySelectorGUI(plugin, player, this, slot).open();
        }, 1L);
    }
    
    private void openCategorySelector(int slot) {
        // Set navigation state to prevent cleanup
        isNavigating = true;
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new CategorySelectorGUI(plugin, player, this, slot).open();
        }, 1L);
    }
    
    private void openArmorSelector(int slot) {
        // Set navigation state to prevent cleanup
        isNavigating = true;
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new ArmorSelectorGUI(plugin, player, this, slot).open();
        }, 1L);
    }
    
    private void openItemModificationMenu(int slot, ItemStack item) {
        // Set navigation state to prevent cleanup
        isNavigating = true;
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new EnhancedItemModificationGUI(plugin, player, this, slot, item).open();
        }, 1L);
    }
    
    private void openKitSettings() {
        // Set navigation state to prevent cleanup
        isNavigating = true;
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new KitSettingsGUI(plugin, player, this, kitName).open();
        }, 1L);
    }
    
    private ItemStack getCurrentItemInSlot(int slot) {
        if (slot < 36) {
            return kitContents[slot];
        } else if (slot < 40) {
            return kitArmor[slot - 36];
        } else if (slot == 40) {
            return offhandItem;
        }
        return null;
    }
    
    private boolean isPlaceholderItem(ItemStack item) {
        if (item == null) return true;
        Material type = item.getType();
        return type == Material.RED_STAINED_GLASS_PANE;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player closer = (Player) event.getPlayer();
        
        if (closer.getUniqueId().equals(player.getUniqueId()) && event.getInventory().equals(gui)) {
            // Only cleanup if this is a final close (not navigation)
            if (isActive && !isNavigating) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (isActive && !isNavigating) {
                        saveKit();
                    }
                }, 5L);
            }
        }
    }
    
    private void forceCleanup() {
        isActive = false;
        isBulkMode = false;
        bulkItem = null;
        isNavigating = false;
        activeGuis.remove(player.getUniqueId());
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        player.closeInventory();
    }
    
    public void setSlotItem(int slot, ItemStack item) {
        if (slot < 36) {
            kitContents[slot] = item;
            updateSlot(slot);
        } else if (slot < 40) {
            kitArmor[slot - 36] = item;
            updateArmorSlot(slot - 36);
        } else if (slot == 40) {
            offhandItem = item;
            updateOffhandSlot();
        }
    }
    
    public void clearSlot(int slot) {
        setSlotItem(slot, null);
    }
    
    private void clearAllSlots() {
        for (int i = 0; i < 36; i++) {
            kitContents[i] = null;
            updateSlot(i);
        }
        for (int i = 0; i < 4; i++) {
            kitArmor[i] = null;
            updateArmorSlot(i);
        }
        offhandItem = null;
        updateOffhandSlot();
        exitBulkMode(); // Exit bulk mode when clearing
        player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("all slots cleared!"));
    }
    
    private void saveKit() {
        // Create extended contents array to include offhand
        ItemStack[] extendedContents = new ItemStack[37];
        System.arraycopy(kitContents, 0, extendedContents, 0, 36);
        extendedContents[36] = offhandItem;
        
        Kit kit = new Kit(kitName, kitName, extendedContents, kitArmor.clone());
        plugin.getKitManager().saveKit(player.getUniqueId(), kit);
        
        // Show title message
        player.sendTitle(ChatColor.GREEN + kitName + FontUtils.toSmallCaps(" saved!"), ChatColor.YELLOW + FontUtils.toSmallCaps("successfully saved"), 10, 40, 10);
        player.sendMessage(ChatColor.GREEN + FontUtils.toSmallCaps("kit '") + kitName + FontUtils.toSmallCaps("' saved successfully!"));
        forceCleanup();
    }
    
    public void refreshAndReopen() {
        // Reset navigation state
        isNavigating = false;
        
        // Refresh the GUI content
        setupGUI();
        
        // If the player doesn't have this inventory open, open it
        if (!player.getOpenInventory().getTopInventory().equals(gui)) {
            player.openInventory(gui);
        }
    }
    
    public void setBulkItem(ItemStack item) {
        enterBulkMode(item);
    }
    
    public boolean isActive() {
        return isActive;
    }
}