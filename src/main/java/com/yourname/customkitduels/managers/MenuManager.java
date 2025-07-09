package com.yourname.customkitduels.managers;

import com.yourname.customkitduels.CustomKitDuels;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages customizable menu configurations
 */
public class MenuManager {
    
    private final CustomKitDuels plugin;
    private final File menusFolder;
    private final Map<String, FileConfiguration> menuConfigs;
    
    public MenuManager(CustomKitDuels plugin) {
        this.plugin = plugin;
        this.menusFolder = new File(plugin.getDataFolder(), "menus");
        this.menuConfigs = new HashMap<>();
        
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
        }
        
        loadMenus();
    }
    
    private void loadMenus() {
        // Load all menu files
        File[] menuFiles = menusFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (menuFiles != null) {
            for (File file : menuFiles) {
                String menuName = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                menuConfigs.put(menuName, config);
                if (plugin.isDebugEnabled()) {
                    plugin.getLogger().info("Loaded menu configuration: " + menuName);
                }
            }
        }
    }
    
    public void reloadMenus() {
        menuConfigs.clear();
        loadMenus();
        if (plugin.isDebugEnabled()) {
            plugin.getLogger().info("Reloaded all menu configurations");
        }
    }
    
    public FileConfiguration getMenuConfig(String menuName) {
        return menuConfigs.get(menuName);
    }
    
    public String getMenuTitle(String menuName) {
        FileConfiguration config = getMenuConfig(menuName);
        if (config == null) return "Menu";
        
        String title = config.getString("title", "Menu");
        return ChatColor.translateAlternateColorCodes('&', title);
    }
    
    public ItemStack createMenuItem(String menuName, String itemKey, Map<String, String> placeholders) {
        FileConfiguration config = getMenuConfig(menuName);
        if (config == null) return new ItemStack(Material.STONE);
        
        String basePath = "items." + itemKey;
        
        // Get material
        String materialName = config.getString(basePath + ".material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
            plugin.getLogger().warning("Invalid material in menu " + menuName + ": " + materialName);
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name
        String name = config.getString(basePath + ".name", "Item");
        name = replacePlaceholders(name, placeholders);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        
        // Set lore
        List<String> lore = config.getStringList(basePath + ".lore");
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            line = replacePlaceholders(line, placeholders);
            processedLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(processedLore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public int getSlot(String menuName, String slotKey) {
        FileConfiguration config = getMenuConfig(menuName);
        if (config == null) return 0;
        
        return config.getInt("slots." + slotKey, 0);
    }
    
    public List<Integer> getSlotList(String menuName, String slotKey) {
        FileConfiguration config = getMenuConfig(menuName);
        if (config == null) return new ArrayList<>();
        
        return config.getIntegerList("slots." + slotKey);
    }
    
    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null) return text;
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        
        return text;
    }
}