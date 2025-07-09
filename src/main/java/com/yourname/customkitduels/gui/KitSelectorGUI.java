package com.yourname.customkitduels.gui;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.Kit;
import com.yourname.customkitduels.managers.MenuManager;
import com.yourname.customkitduels.utils.FontUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class KitSelectorGUI implements Listener {
    
    private final CustomKitDuels plugin;
    private final Player challenger;
    private final Player target;
    private final Inventory gui;
    private final List<Kit> playerKits;
    private static final Map<UUID, KitSelectorGUI> activeGuis = new HashMap<>();
    private boolean isActive = true;
    
    public KitSelectorGUI(CustomKitDuels plugin, Player challenger, Player target) {
        this.plugin = plugin;
        this.challenger = challenger;
        this.target = target;
        this.playerKits = plugin.getKitManager().getPlayerKits(challenger.getUniqueId());
        
        String title = plugin.getMenuManager().getMenuTitle("kits");
        this.gui = Bukkit.createInventory(null, 27, title);
        
        plugin.getLogger().info("[DEBUG] Creating KitSelectorGUI for challenger " + challenger.getName() + " vs " + target.getName());
        
        setupGUI();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void setupGUI() {
        gui.clear();
        
        MenuManager menuManager = plugin.getMenuManager();
        
        if (playerKits.isEmpty()) {
            // No kits message
            ItemStack noKits = menuManager.createMenuItem("kits", "no_kits", null);
            gui.setItem(menuManager.getSlot("kits", "no_kits"), noKits);
            return;
        }
        
        // Add kit items
        List<Integer> kitSlots = menuManager.getSlotList("kits", "kit_slots");
        for (int i = 0; i < Math.min(playerKits.size(), kitSlots.size()); i++) {
            Kit kit = playerKits.get(i);
            
            // Get kit settings for display
            double hearts = plugin.getKitManager().getKitHearts(challenger.getUniqueId(), kit.getName());
            boolean naturalRegen = plugin.getKitManager().getKitNaturalRegen(challenger.getUniqueId(), kit.getName());
            
            Map<String, String> kitPlaceholders = new HashMap<>();
            kitPlaceholders.put("kit_name", kit.getName());
            kitPlaceholders.put("target", target.getName());
            kitPlaceholders.put("hearts", String.valueOf(hearts));
            kitPlaceholders.put("natural_regen", naturalRegen ? "Enabled" : "Disabled");
            
            ItemStack kitItem = menuManager.createMenuItem("kits", "kit", kitPlaceholders);
            gui.setItem(kitSlots.get(i), kitItem);
        }
        
        // Cancel button
        ItemStack cancelItem = menuManager.createMenuItem("kits", "cancel", null);
        gui.setItem(menuManager.getSlot("kits", "cancel"), cancelItem);
    }
    
    public void open() {
        plugin.getLogger().info("[DEBUG] Opening KitSelectorGUI for " + challenger.getName());
        
        KitSelectorGUI existing = activeGuis.get(challenger.getUniqueId());
        if (existing != null && existing != this) {
            plugin.getLogger().info("[DEBUG] Cleaning up existing KitSelectorGUI for " + challenger.getName());
            existing.forceCleanup();
        }
        
        activeGuis.put(challenger.getUniqueId(), this);
        isActive = true;
        challenger.openInventory(gui);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        
        if (!clicker.equals(challenger) || !event.getInventory().equals(gui) || !isActive) {
            return;
        }
        
        event.setCancelled(true);
        int slot = event.getSlot();
        
        plugin.getLogger().info("[DEBUG] KitSelectorGUI click event - Player: " + challenger.getName() + ", Slot: " + slot);
        
        MenuManager menuManager = plugin.getMenuManager();
        int cancelSlot = menuManager.getSlot("kits", "cancel");
        List<Integer> kitSlots = menuManager.getSlotList("kits", "kit_slots");
        
        if (slot == cancelSlot) { // Cancel
            plugin.getLogger().info("[DEBUG] Duel cancelled by " + challenger.getName());
            challenger.sendMessage(ChatColor.RED + FontUtils.toSmallCaps("duel request cancelled."));
            forceCleanup();
            return;
        }
        
        // Handle kit selection
        for (int i = 0; i < kitSlots.size(); i++) {
            if (slot == kitSlots.get(i) && i < playerKits.size()) {
                Kit selectedKit = playerKits.get(i);
                plugin.getLogger().info("[DEBUG] Selected kit: " + selectedKit.getName());
                
                // Open rounds selector
                forceCleanup();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RoundsSelectorGUI(plugin, challenger, target, selectedKit).open();
                }, 1L);
                return;
            }
        }
    }
    
    private void forceCleanup() {
        plugin.getLogger().info("[DEBUG] Force cleanup KitSelectorGUI for " + challenger.getName());
        isActive = false;
        activeGuis.remove(challenger.getUniqueId());
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        challenger.closeInventory();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player closer = (Player) event.getPlayer();
        
        if (closer.equals(challenger) && event.getInventory().equals(gui)) {
            plugin.getLogger().info("[DEBUG] KitSelectorGUI inventory closed by " + challenger.getName() + ", Active: " + isActive);
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isActive && activeGuis.containsKey(challenger.getUniqueId())) {
                    plugin.getLogger().info("[DEBUG] Final cleanup KitSelectorGUI for " + challenger.getName());
                    forceCleanup();
                }
            }, 3L);
        }
    }
}