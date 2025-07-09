package com.yourname.customkitduels.gui;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.utils.FontUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EnhancedItemModificationGUI implements Listener {
    
    private final CustomKitDuels plugin;
    private final Player player;
    private final KitEditorGUI parentGUI;
    private final int targetSlot;
    private ItemStack targetItem;
    private final Inventory gui;
    private static final Map<UUID, EnhancedItemModificationGUI> activeGuis = new HashMap<>();
    private static final Set<UUID> waitingForCustomAmount = new HashSet<>();
    private boolean isActive = true;
    private boolean isNavigating = false;
    private int currentPage = 0;
    
    // Store enchantment-slot mapping for proper click handling
    private final Map<Integer, EnchantmentData> slotToEnchantment = new HashMap<>();
    
    private static class EnchantmentData {
        final Enchantment enchantment;
        final int level;
        
        EnchantmentData(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }
    
    public EnhancedItemModificationGUI(CustomKitDuels plugin, Player player, KitEditorGUI parentGUI, int targetSlot, ItemStack targetItem) {
        this.plugin = plugin;
        this.player = player;
        this.parentGUI = parentGUI;
        this.targetSlot = targetSlot;
        this.targetItem = targetItem.clone();
        this.gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + FontUtils.toSmallCaps("modify item"));
        
        plugin.getLogger().info("[DEBUG] Creating EnhancedItemModificationGUI for player " + player.getName() + " slot " + targetSlot);
        
        setupGUI();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void setupGUI() {
        gui.clear();
        slotToEnchantment.clear();
        
        if (isStackableItem(targetItem)) {
            setupStackableItemGUI();
        } else if (isEnchantableItem(targetItem)) {
            setupEnchantableItemGUI();
        } else {
            setupBasicItemGUI();
        }
        
        // Display the item being modified in bottom row
        gui.setItem(45, targetItem.clone());
        
        // Remove item option
        ItemStack removeItem = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeItem.getItemMeta();
        removeMeta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps("remove item"));
        removeMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("remove this item from the slot")));
        removeItem.setItemMeta(removeMeta);
        gui.setItem(53, removeItem);
        
        // Back button
        ItemStack backItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + FontUtils.toSmallCaps("back"));
        backMeta.setLore(Arrays.asList(ChatColor.GRAY + FontUtils.toSmallCaps("return to kit editor")));
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);
    }
    
    private void setupStackableItemGUI() {
        // Title
        ItemStack titleItem = new ItemStack(Material.BOOK);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName(ChatColor.YELLOW + FontUtils.toSmallCaps("change item count"));
        titleMeta.setLore(Arrays.asList(
            ChatColor.GRAY + FontUtils.toSmallCaps("select the amount you want"),
            ChatColor.GRAY + FontUtils.toSmallCaps("current: ") + targetItem.getAmount()
        ));
        titleItem.setItemMeta(titleMeta);
        gui.setItem(0, titleItem);
        
        // Amount options: 1, 8, 16, 32, 64
        int[] amounts = {1, 8, 16, 32, 64};
        int[] slots = {19, 20, 21, 22, 23};
        
        for (int i = 0; i < amounts.length; i++) {
            ItemStack amountItem = targetItem.clone();
            amountItem.setAmount(Math.min(amounts[i], targetItem.getMaxStackSize()));
            
            ItemMeta meta = amountItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + FontUtils.toSmallCaps("amount: ") + amounts[i]);
                meta.setLore(Arrays.asList(ChatColor.YELLOW + FontUtils.toSmallCaps("click to set amount to ") + amounts[i]));
                amountItem.setItemMeta(meta);
            }
            
            gui.setItem(slots[i], amountItem);
        }
        
        // Custom amount option
        ItemStack customItem = new ItemStack(Material.PAPER);
        ItemMeta customMeta = customItem.getItemMeta();
        customMeta.setDisplayName(ChatColor.AQUA + FontUtils.toSmallCaps("custom amount"));
        customMeta.setLore(Arrays.asList(
            ChatColor.GRAY + FontUtils.toSmallCaps("enter a custom amount in chat"),
            ChatColor.YELLOW + FontUtils.toSmallCaps("max: ") + targetItem.getMaxStackSize()
        ));
        customItem.setItemMeta(customMeta);
        gui.setItem(24, customItem);
    }
    
    private void setupEnchantableItemGUI() {
        // Get organized enchantment rows for this item type
        List<EnchantmentRow> enchantmentRows = getOrganizedEnchantmentRows(targetItem.getType());
        
        if (enchantmentRows.isEmpty()) {
            setupBasicItemGUI();
            return;
        }
        
        // Place enchantments in organized rows
        int currentRow = 0;
        for (EnchantmentRow row : enchantmentRows) {
            if (currentRow >= 4) break; // Only use first 4 rows (0-35 slots)
            
            placeEnchantmentRow(row, currentRow);
            currentRow++;
        }
        
        // Durability button
        if (targetItem.getType().getMaxDurability() > 0) {
            ItemStack durabilityItem = new ItemStack(Material.ANVIL);
            ItemMeta durabilityMeta = durabilityItem.getItemMeta();
            durabilityMeta.setDisplayName(ChatColor.YELLOW + FontUtils.toSmallCaps("change durability"));
            durabilityMeta.setLore(Arrays.asList(
                ChatColor.GRAY + FontUtils.toSmallCaps("modify item durability"),
                ChatColor.YELLOW + FontUtils.toSmallCaps("click to open durability editor")
            ));
            durabilityItem.setItemMeta(durabilityMeta);
            gui.setItem(47, durabilityItem);
        }
        
        // Rename button
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        renameMeta.setDisplayName(ChatColor.AQUA + FontUtils.toSmallCaps("rename item"));
        renameMeta.setLore(Arrays.asList(
            ChatColor.GRAY + FontUtils.toSmallCaps("give your item a custom name"),
            ChatColor.YELLOW + FontUtils.toSmallCaps("click to rename")
        ));
        renameItem.setItemMeta(renameMeta);
        gui.setItem(48, renameItem);
    }
    
    /**
     * Place an enchantment row in the GUI with proper spacing
     */
    private void placeEnchantmentRow(EnchantmentRow row, int rowIndex) {
        int startSlot = rowIndex * 9; // Each row starts at slot 0, 9, 18, 27, etc.
        int currentSlot = startSlot;
        
        for (Enchantment enchant : row.enchantments) {
            if (currentSlot >= startSlot + 9) break; // Don't exceed row boundary
            
            // Place all levels of this enchantment
            for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                if (currentSlot >= startSlot + 9) break; // Don't exceed row boundary
                
                ItemStack enchantBook = createEnchantmentBookWithStack(enchant, level);
                gui.setItem(currentSlot, enchantBook);
                slotToEnchantment.put(currentSlot, new EnchantmentData(enchant, level));
                currentSlot++;
            }
            
            // Add gap after enchantment (except for last enchantment in row)
            if (row.enchantments.indexOf(enchant) < row.enchantments.size() - 1 && currentSlot < startSlot + 9) {
                currentSlot++; // Skip one slot for gap
            }
        }
    }
    
    /**
     * Get organized enchantment rows for specific item types
     */
    private List<EnchantmentRow> getOrganizedEnchantmentRows(Material material) {
        List<EnchantmentRow> rows = new ArrayList<>();
        String materialName = material.toString();
        
        // SWORDS - Combat enchantments organized by type
        if (materialName.contains("SWORD")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SHARPNESS, Enchantment.UNBREAKING))); // Damage + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SMITE, Enchantment.KNOCKBACK))); // Special damage + Utility
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BANE_OF_ARTHROPODS, Enchantment.FIRE_ASPECT))); // Special damage + Fire
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.LOOTING, Enchantment.SWEEPING_EDGE, Enchantment.MENDING))); // Utility enchants
        }
        
        // AXES - Tool + Combat enchantments
        else if (materialName.contains("AXE")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.EFFICIENCY, Enchantment.UNBREAKING))); // Tool enchants
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FORTUNE, Enchantment.SHARPNESS))); // Fortune + Combat
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SILK_TOUCH, Enchantment.SMITE))); // Silk Touch + Combat
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BANE_OF_ARTHROPODS, Enchantment.MENDING))); // Combat + Utility
        }
        
        // PICKAXES - Mining enchantments ONLY (NO SHARPNESS!)
        else if (materialName.contains("PICKAXE")) {
            // PICKAXES: ABSOLUTELY NO COMBAT ENCHANTMENTS - ONLY MINING
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.EFFICIENCY))); // Mining speed only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FORTUNE))); // Fortune only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SILK_TOUCH))); // Silk touch only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability only
        }
        
        // SHOVELS - Digging enchantments ONLY (NO COMBAT ENCHANTS!)
        else if (materialName.contains("SHOVEL")) {
            // SHOVELS: ABSOLUTELY NO COMBAT ENCHANTMENTS - ONLY DIGGING
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.EFFICIENCY))); // Digging speed only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FORTUNE))); // Fortune only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SILK_TOUCH))); // Silk touch only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability only
        }
        
        // HOES - Farming enchantments ONLY (NO COMBAT ENCHANTS!)
        else if (materialName.contains("HOE")) {
            // HOES: ABSOLUTELY NO COMBAT ENCHANTMENTS - ONLY FARMING
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.EFFICIENCY))); // Farming speed only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FORTUNE))); // Fortune only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.SILK_TOUCH))); // Silk touch only
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability only
        }
        
        // BOWS - Ranged combat enchantments
        else if (material == Material.BOW) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.POWER, Enchantment.UNBREAKING))); // Damage + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PUNCH, Enchantment.INFINITY))); // Knockback + Infinity
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FLAME, Enchantment.MENDING))); // Fire + Mending
        }
        
        // CROSSBOWS - Crossbow specific enchantments
        else if (material == Material.CROSSBOW) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.QUICK_CHARGE, Enchantment.UNBREAKING))); // Speed + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.MULTISHOT))); // Multishot (1 level)
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PIERCING, Enchantment.MENDING))); // Piercing + Mending
        }
        
        // TRIDENTS - Trident specific enchantments
        else if (material == Material.TRIDENT) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.LOYALTY, Enchantment.UNBREAKING))); // Loyalty + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.CHANNELING, Enchantment.IMPALING))); // Lightning + Damage
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.RIPTIDE, Enchantment.MENDING))); // Riptide + Mending
        }
        
        // MACES - Mace specific enchantments (NO SHARPNESS!)
        else if (material == Material.MACE) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.DENSITY, Enchantment.UNBREAKING))); // Mace specific + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BREACH, Enchantment.SMITE))); // Mace specific + Combat
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.WIND_BURST, Enchantment.BANE_OF_ARTHROPODS))); // Mace specific + Combat
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.MENDING))); // Utility
        }
        
        // HELMETS - Head armor enchantments
        else if (materialName.contains("HELMET")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PROTECTION, Enchantment.UNBREAKING))); // Protection + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FIRE_PROTECTION, Enchantment.THORNS))); // Fire protection + Thorns
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION))); // Explosion + Projectile
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.AQUA_AFFINITY, Enchantment.RESPIRATION, Enchantment.MENDING))); // Utility enchants
        }
        
        // CHESTPLATES - Chest armor enchantments
        else if (materialName.contains("CHESTPLATE")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PROTECTION, Enchantment.UNBREAKING))); // Protection + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FIRE_PROTECTION, Enchantment.THORNS))); // Fire protection + Thorns
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION))); // Explosion + Projectile
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.MENDING))); // Utility
        }
        
        // LEGGINGS - Leg armor enchantments
        else if (materialName.contains("LEGGINGS")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PROTECTION, Enchantment.UNBREAKING))); // Protection + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FIRE_PROTECTION, Enchantment.THORNS))); // Fire protection + Thorns
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION))); // Explosion + Projectile
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.MENDING))); // Utility
        }
        
        // BOOTS - Foot armor enchantments + movement (utility enchants at end)
        else if (materialName.contains("BOOTS")) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.PROTECTION, Enchantment.UNBREAKING))); // Protection + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FIRE_PROTECTION, Enchantment.THORNS))); // Fire protection + Thorns
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION))); // Explosion + Projectile
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.FEATHER_FALLING, Enchantment.DEPTH_STRIDER, Enchantment.FROST_WALKER, Enchantment.SOUL_SPEED, Enchantment.MENDING))); // Utility enchants
        }
        
        // FISHING RODS - Fishing enchantments
        else if (material == Material.FISHING_ROD) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.LUCK_OF_THE_SEA, Enchantment.UNBREAKING))); // Luck + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.LURE, Enchantment.MENDING))); // Speed + Mending
        }
        
        // SHIELDS - Shield enchantments
        else if (material == Material.SHIELD) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability + Mending
        }
        
        // SHEARS - Shears enchantments
        else if (material == Material.SHEARS) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.EFFICIENCY, Enchantment.UNBREAKING))); // Speed + Durability
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.MENDING))); // Mending
        }
        
        // FLINT AND STEEL - Fire starter enchantments
        else if (material == Material.FLINT_AND_STEEL) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability + Mending
        }
        
        // ELYTRA - Flying enchantments
        else if (material == Material.ELYTRA) {
            rows.add(new EnchantmentRow(Arrays.asList(Enchantment.UNBREAKING, Enchantment.MENDING))); // Durability + Mending
        }
        
        return rows;
    }
    
    /**
     * Helper class to organize enchantments into rows
     */
    private static class EnchantmentRow {
        final List<Enchantment> enchantments;
        
        EnchantmentRow(List<Enchantment> enchantments) {
            this.enchantments = enchantments;
        }
    }
    
    /**
     * Create enchantment book with stack amount equal to enchantment level
     */
    private ItemStack createEnchantmentBookWithStack(Enchantment enchant, int level) {
        ItemStack enchantBook = new ItemStack(Material.ENCHANTED_BOOK, 1); // Always 1 stack - level shown in name
        
        ItemMeta meta = enchantBook.getItemMeta();
        
        String enchantName = formatEnchantmentName(enchant.getKey().getKey());
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + FontUtils.toSmallCaps(enchantName + " " + level));
        
        int currentLevel = targetItem.getEnchantmentLevel(enchant);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + FontUtils.toSmallCaps("current level: ") + (currentLevel > 0 ? currentLevel : FontUtils.toSmallCaps("none")));
        lore.add(ChatColor.YELLOW + FontUtils.toSmallCaps("click to apply ") + enchantName + " " + level);
        lore.add(ChatColor.AQUA + FontUtils.toSmallCaps("level: ") + level);
        
        if (currentLevel == level) {
            lore.add(ChatColor.GREEN + "✓ " + FontUtils.toSmallCaps("currently applied"));
        } else if (currentLevel > 0 && currentLevel != level) {
            lore.add(ChatColor.YELLOW + "⚠ " + FontUtils.toSmallCaps("will replace current level"));
        }
        
        // Check for conflicts
        if (hasConflictingEnchantments(enchant)) {
            lore.add(ChatColor.RED + "⚠ " + FontUtils.toSmallCaps("will remove conflicting enchants"));
        }
        
        meta.setLore(lore);
        enchantBook.setItemMeta(meta);
        
        return enchantBook;
    }
    
    private boolean hasConflictingEnchantments(Enchantment enchantment) {
        Map<Enchantment, Integer> currentEnchants = targetItem.getEnchantments();
        
        for (Enchantment current : currentEnchants.keySet()) {
            if (areEnchantmentsConflicting(enchantment, current)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean areEnchantmentsConflicting(Enchantment ench1, Enchantment ench2) {
        if (ench1.equals(ench2)) return false;
        
        // Define conflict groups
        Set<Enchantment> protectionEnchants = new HashSet<>(Arrays.asList(
            Enchantment.PROTECTION, Enchantment.FIRE_PROTECTION, 
            Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION
        ));
        
        Set<Enchantment> damageEnchants = new HashSet<>(Arrays.asList(
            Enchantment.SHARPNESS, Enchantment.SMITE, Enchantment.BANE_OF_ARTHROPODS
        ));
        
        Set<Enchantment> fortuneSilkTouch = new HashSet<>(Arrays.asList(
            Enchantment.FORTUNE, Enchantment.SILK_TOUCH
        ));
        
        Set<Enchantment> infinityMending = new HashSet<>(Arrays.asList(
            Enchantment.INFINITY, Enchantment.MENDING
        ));
        
        Set<Enchantment> depthStriderFrostWalker = new HashSet<>(Arrays.asList(
            Enchantment.DEPTH_STRIDER, Enchantment.FROST_WALKER
        ));
        
        Set<Enchantment> multishotPiercing = new HashSet<>(Arrays.asList(
            Enchantment.MULTISHOT, Enchantment.PIERCING
        ));
        
        Set<Enchantment> loyaltyRiptide = new HashSet<>(Arrays.asList(
            Enchantment.LOYALTY, Enchantment.RIPTIDE
        ));
        
        Set<Enchantment> channelingRiptide = new HashSet<>(Arrays.asList(
            Enchantment.CHANNELING, Enchantment.RIPTIDE
        ));
        
        // Check all conflict groups
        return (protectionEnchants.contains(ench1) && protectionEnchants.contains(ench2)) ||
               (damageEnchants.contains(ench1) && damageEnchants.contains(ench2)) ||
               (fortuneSilkTouch.contains(ench1) && fortuneSilkTouch.contains(ench2)) ||
               (infinityMending.contains(ench1) && infinityMending.contains(ench2)) ||
               (depthStriderFrostWalker.contains(ench1) && depthStriderFrostWalker.contains(ench2)) ||
               (multishotPiercing.contains(ench1) && multishotPiercing.contains(ench2)) ||
               (loyaltyRiptide.contains(ench1) && loyaltyRiptide.contains(ench2)) ||
               (channelingRiptide.contains(ench1) && channelingRiptide.contains(ench2));
    }
    
    private void setupBasicItemGUI() {
        // For non-stackable, non-enchantable items, just show basic options
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + FontUtils.toSmallCaps("item options"));
        infoMeta.setLore(Arrays.asList(
            ChatColor.GRAY + FontUtils.toSmallCaps("this item cannot be modified"),
            ChatColor.GRAY + FontUtils.toSmallCaps("you can only remove it")
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(22, infoItem);
    }
    
    private boolean isStackableItem(ItemStack item) {
        return item.getMaxStackSize() > 1;
    }
    
    private boolean isEnchantableItem(ItemStack item) {
        Material type = item.getType();
        return type.toString().contains("SWORD") || 
               type.toString().contains("AXE") ||
               type.toString().contains("PICKAXE") ||
               type.toString().contains("SHOVEL") ||
               type.toString().contains("HOE") ||
               type.toString().contains("BOW") ||
               type.toString().contains("CROSSBOW") ||
               type.toString().contains("TRIDENT") ||
               type.toString().contains("HELMET") ||
               type.toString().contains("CHESTPLATE") ||
               type.toString().contains("LEGGINGS") ||
               type.toString().contains("BOOTS") ||
               type == Material.SHIELD ||
               type == Material.FISHING_ROD ||
               type == Material.SHEARS ||
               type == Material.FLINT_AND_STEEL ||
               type == Material.ELYTRA ||
               type == Material.MACE;
    }
    
    private String formatEnchantmentName(String key) {
        String[] words = key.split("_");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        
        return formatted.toString();
    }
    
    public void open() {
        plugin.getLogger().info("[DEBUG] Opening EnhancedItemModificationGUI for " + player.getName());
        
        EnhancedItemModificationGUI existing = activeGuis.get(player.getUniqueId());
        if (existing != null && existing != this) {
            plugin.getLogger().info("[DEBUG] Cleaning up existing EnhancedItemModificationGUI for " + player.getName());
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
        
        if (!clicker.equals(player) || !event.getInventory().equals(gui) || !isActive || isNavigating) {
            return;
        }
        
        event.setCancelled(true);
        int slot = event.getSlot();
        
        plugin.getLogger().info("[DEBUG] EnhancedItemModificationGUI click event - Player: " + player.getName() + ", Slot: " + slot);
        
        if (slot == 49) { // Back
            returnToParent();
            return;
        }
        
        if (slot == 53) { // Remove item
            removeItem();
            return;
        }
        
        // Check if it's an enchantment slot
        EnchantmentData enchantData = slotToEnchantment.get(slot);
        if (enchantData != null) {
            applyEnchantment(enchantData.enchantment, enchantData.level);
            return;
        }
        
        if (isStackableItem(targetItem)) {
            handleStackableItemClick(slot);
        } else if (isEnchantableItem(targetItem)) {
            handleEnchantableItemClick(slot);
        }
    }
    
    private void applyEnchantment(Enchantment enchantment, int level) {
        plugin.getLogger().info("[DEBUG] Applying enchantment " + enchantment.getKey().getKey() + " level " + level + " to item");
        
        // Remove conflicting enchantments
        removeConflictingEnchantments(enchantment);
        
        // Apply the enchantment
        targetItem.addUnsafeEnchantment(enchantment, level);
        parentGUI.setSlotItem(targetSlot, targetItem);
        
        String enchantName = formatEnchantmentName(enchantment.getKey().getKey());
        player.sendMessage(ChatColor.GREEN + FontUtils.toSmallCaps("applied ") + enchantName + " " + level + "!");
        
        // Refresh GUI to show updated enchantments
        setupGUI();
    }
    
    private void removeConflictingEnchantments(Enchantment newEnchantment) {
        Map<Enchantment, Integer> currentEnchants = new HashMap<>(targetItem.getEnchantments());
        
        for (Enchantment current : currentEnchants.keySet()) {
            if (areEnchantmentsConflicting(newEnchantment, current)) {
                targetItem.removeEnchantment(current);
                String enchantName = formatEnchantmentName(current.getKey().getKey());
                player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("removed conflicting ") + enchantName);
            }
        }
    }
    
    private void handleStackableItemClick(int slot) {
        if (slot >= 19 && slot <= 23) {
            // Amount buttons
            int[] amounts = {1, 8, 16, 32, 64};
            int index = slot - 19;
            if (index < amounts.length) {
                int newAmount = Math.min(amounts[index], targetItem.getMaxStackSize());
                targetItem.setAmount(newAmount);
                parentGUI.setSlotItem(targetSlot, targetItem);
                player.sendMessage(ChatColor.GREEN + FontUtils.toSmallCaps("item amount set to ") + newAmount + "!");
                returnToParent();
            }
        } else if (slot == 24) {
            // Custom amount
            requestCustomAmount();
        }
    }
    
    private void handleEnchantableItemClick(int slot) {
        if (slot == 47) {
            // Durability editor - open anvil GUI
            openDurabilityAnvilGUI();
        } else if (slot == 48) {
            // Rename item
            requestItemName();
        }
    }
    
    private void openDurabilityAnvilGUI() {
        plugin.getLogger().info("[DEBUG] Opening durability anvil GUI for " + player.getName());
        isNavigating = true;
        forceCleanup();
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            new DurabilityAnvilGUI(plugin, player, parentGUI, targetSlot, targetItem).open();
        }, 1L);
    }
    
    private void requestCustomAmount() {
        plugin.getLogger().info("[DEBUG] Requesting custom amount from " + player.getName());
        waitingForCustomAmount.add(player.getUniqueId());
        isNavigating = true;
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("enter the amount (1-") + targetItem.getMaxStackSize() + FontUtils.toSmallCaps(") in chat:"));
    }
    
    private void requestItemName() {
        plugin.getLogger().info("[DEBUG] Requesting item name from " + player.getName());
        waitingForCustomAmount.add(player.getUniqueId()); // Reuse the same set
        isNavigating = true;
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("enter the new item name in chat:"));
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!waitingForCustomAmount.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        
        Player chatPlayer = event.getPlayer();
        if (!chatPlayer.equals(player)) {
            return;
        }
        
        event.setCancelled(true);
        waitingForCustomAmount.remove(player.getUniqueId());
        
        String message = event.getMessage().trim();
        
        // Check if it's a number (for amount) or text (for name)
        try {
            int amount = Integer.parseInt(message);
            if (amount < 1 || amount > targetItem.getMaxStackSize()) {
                player.sendMessage(ChatColor.RED + FontUtils.toSmallCaps("invalid amount! must be between 1 and ") + targetItem.getMaxStackSize());
            } else {
                targetItem.setAmount(amount);
                parentGUI.setSlotItem(targetSlot, targetItem);
                player.sendMessage(ChatColor.GREEN + FontUtils.toSmallCaps("item amount set to ") + amount + "!");
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    returnToParent();
                });
                return;
            }
        } catch (NumberFormatException e) {
            // It's a name, not a number
            ItemMeta meta = targetItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', message));
                targetItem.setItemMeta(meta);
                parentGUI.setSlotItem(targetSlot, targetItem);
                player.sendMessage(ChatColor.GREEN + FontUtils.toSmallCaps("item renamed to: ") + message);
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    returnToParent();
                });
                return;
            }
        }
        
        // Reopen the modification GUI if there was an error
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            isNavigating = false;
            player.openInventory(gui);
        });
    }
    
    private void removeItem() {
        plugin.getLogger().info("[DEBUG] Removing item from slot " + targetSlot);
        parentGUI.clearSlot(targetSlot);
        player.sendMessage(ChatColor.YELLOW + FontUtils.toSmallCaps("item removed from slot!"));
        returnToParent();
    }
    
    private void returnToParent() {
        plugin.getLogger().info("[DEBUG] Returning to parent GUI for player " + player.getName());
        isNavigating = true;
        forceCleanup();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            parentGUI.refreshAndReopen();
        }, 1L);
    }
    
    public void updateItem(ItemStack newItem) {
        this.targetItem = newItem.clone();
        parentGUI.setSlotItem(targetSlot, targetItem);
        setupGUI(); // Refresh the modification GUI
    }
    
    public void refreshAndReopen() {
        plugin.getLogger().info("[DEBUG] Refreshing and reopening EnhancedItemModificationGUI for " + player.getName());
        isNavigating = false;
        setupGUI();
        
        if (!player.getOpenInventory().getTopInventory().equals(gui)) {
            player.openInventory(gui);
        }
    }
    
    private void forceCleanup() {
        plugin.getLogger().info("[DEBUG] Force cleanup EnhancedItemModificationGUI for " + player.getName());
        isActive = false;
        activeGuis.remove(player.getUniqueId());
        waitingForCustomAmount.remove(player.getUniqueId());
        slotToEnchantment.clear();
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        player.closeInventory();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player closer = (Player) event.getPlayer();
        
        if (closer.equals(player) && event.getInventory().equals(gui)) {
            plugin.getLogger().info("[DEBUG] EnhancedItemModificationGUI inventory closed by " + player.getName() + ", Active: " + isActive + ", Navigating: " + isNavigating);
            
            // Don't cleanup if waiting for chat input or navigating
            if (waitingForCustomAmount.contains(player.getUniqueId()) || isNavigating) {
                return;
            }
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isActive && !isNavigating && activeGuis.containsKey(player.getUniqueId())) {
                    plugin.getLogger().info("[DEBUG] Final cleanup EnhancedItemModificationGUI for " + player.getName());
                    forceCleanup();
                    parentGUI.refreshAndReopen();
                }
            }, 3L);
        }
    }
}