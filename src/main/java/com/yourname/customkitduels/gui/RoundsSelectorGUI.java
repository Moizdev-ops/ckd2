package com.yourname.customkitduels.gui;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.Kit;
import com.yourname.customkitduels.managers.MenuManager;
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

public class RoundsSelectorGUI implements Listener {
    
    private final CustomKitDuels plugin;
    private final Player challenger;
    private final Player target;
    private final Kit kit;
    private final Inventory gui;
    private static final Map<UUID, RoundsSelectorGUI> activeGuis = new HashMap<>();
    private boolean isActive = true;
    
    public RoundsSelectorGUI(CustomKitDuels plugin, Player challenger, Player target, Kit kit) {
        this.plugin = plugin;
        this.challenger = challenger;
        this.target = target;
        this.kit = kit;
        
        String title = plugin.getMenuManager().getMenuTitle("rounds");
        this.gui = Bukkit.createInventory(null, 27, title);
        
        plugin.getLogger().info("[DEBUG] Creating RoundsSelectorGUI for challenger " + challenger.getName() + " vs " + target.getName());
        
        setupGUI();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void setupGUI() {
        gui.clear();
        
        MenuManager menuManager = plugin.getMenuManager();
        
        // Title item
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("challenger", challenger.getName());
        titlePlaceholders.put("target", target.getName());
        titlePlaceholders.put("kit", kit.getName());
        
        ItemStack titleItem = menuManager.createMenuItem("rounds", "title", titlePlaceholders);
        gui.setItem(menuManager.getSlot("rounds", "title"), titleItem);
        
        // Round options (First to 1-10) in middle row (slots 10-19)
        int startSlot = menuManager.getSlot("rounds", "rounds_start");
        int endSlot = menuManager.getSlot("rounds", "rounds_end");
        
        for (int i = 0; i < 10 && (startSlot + i) <= endSlot; i++) {
            int rounds = i + 1;
            
            Map<String, String> roundPlaceholders = new HashMap<>();
            roundPlaceholders.put("rounds", String.valueOf(rounds));
            
            ItemStack roundItem = menuManager.createMenuItem("rounds", "round_option", roundPlaceholders);
            gui.setItem(startSlot + i, roundItem);
        }
        
        // Cancel button
        ItemStack cancelItem = menuManager.createMenuItem("rounds", "cancel", null);
        gui.setItem(menuManager.getSlot("rounds", "cancel"), cancelItem);
    }
    
    public void open() {
        plugin.getLogger().info("[DEBUG] Opening RoundsSelectorGUI for " + challenger.getName());
        
        RoundsSelectorGUI existing = activeGuis.get(challenger.getUniqueId());
        if (existing != null && existing != this) {
            plugin.getLogger().info("[DEBUG] Cleaning up existing RoundsSelectorGUI for " + challenger.getName());
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
        
        plugin.getLogger().info("[DEBUG] RoundsSelectorGUI click event - Player: " + challenger.getName() + ", Slot: " + slot);
        
        MenuManager menuManager = plugin.getMenuManager();
        int cancelSlot = menuManager.getSlot("rounds", "cancel");
        int startSlot = menuManager.getSlot("rounds", "rounds_start");
        int endSlot = menuManager.getSlot("rounds", "rounds_end");
        
        if (slot == cancelSlot) { // Cancel
            plugin.getLogger().info("[DEBUG] Duel cancelled by " + challenger.getName());
            challenger.sendMessage(ChatColor.RED + "Duel request cancelled.");
            forceCleanup();
            return;
        }
        
        // Handle round selection
        if (slot >= startSlot && slot <= endSlot) {
            int targetRounds = (slot - startSlot) + 1;
            plugin.getLogger().info("[DEBUG] Selected " + targetRounds + " rounds for duel");
            
            // Send duel request with rounds
            plugin.getDuelManager().sendRoundsDuelRequest(challenger, target, kit, targetRounds);
            forceCleanup();
        }
    }
    
    private void forceCleanup() {
        plugin.getLogger().info("[DEBUG] Force cleanup RoundsSelectorGUI for " + challenger.getName());
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
            plugin.getLogger().info("[DEBUG] RoundsSelectorGUI inventory closed by " + challenger.getName() + ", Active: " + isActive);
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isActive && activeGuis.containsKey(challenger.getUniqueId())) {
                    plugin.getLogger().info("[DEBUG] Final cleanup RoundsSelectorGUI for " + challenger.getName());
                    forceCleanup();
                }
            }, 3L);
        }
    }
}