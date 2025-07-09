package com.yourname.customkitduels;

import com.yourname.customkitduels.commands.CommandHandler;
import com.yourname.customkitduels.managers.*;
import com.yourname.customkitduels.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomKitDuels extends JavaPlugin {
    
    private static CustomKitDuels instance;
    private KitManager kitManager;
    private ArenaManager arenaManager;
    private DuelManager duelManager;
    private CategoryManager categoryManager;
    private ScoreboardManager scoreboardManager;
    private SpawnManager spawnManager;
    private HealthDisplayManager healthDisplayManager;
    private MenuManager menuManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Fancy startup messages with proper colors
        getServer().getConsoleSender().sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        getServer().getConsoleSender().sendMessage("§c§l                    CustomKit Duels v1.0.0");
        getServer().getConsoleSender().sendMessage("§7                     Made with §c❤ §7by §emoiz");
        getServer().getConsoleSender().sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        getServer().getConsoleSender().sendMessage("§a[CustomKit] §7Starting plugin initialization...");
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        if (isDebugEnabled()) getLogger().info("Loading category manager...");
        categoryManager = new CategoryManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading kit manager...");
        kitManager = new KitManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading arena manager...");
        arenaManager = new ArenaManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading health display manager...");
        healthDisplayManager = new HealthDisplayManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading scoreboard manager...");
        scoreboardManager = new ScoreboardManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading spawn manager...");
        spawnManager = new SpawnManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading menu manager...");
        menuManager = new MenuManager(this);
        
        if (isDebugEnabled()) getLogger().info("Loading duel manager...");
        duelManager = new DuelManager(this);
        
        // Register commands
        if (isDebugEnabled()) getLogger().info("Registering commands...");
        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("customkit").setExecutor(commandHandler);
        getCommand("customkit").setTabCompleter(commandHandler);
        
        // Register listeners
        if (isDebugEnabled()) getLogger().info("Registering event listeners...");
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        // Setup health display for already online players (in case of /reload)
        getServer().getScheduler().runTaskLater(this, () -> {
            healthDisplayManager.setupHealthDisplayForAll();
        }, 20L);
        
        // Final startup message
        getServer().getConsoleSender().sendMessage("§a[CustomKit] §7Plugin enabled successfully!");
        getServer().getConsoleSender().sendMessage("§a[CustomKit] §7Ready for epic duels! §c❤");
        getServer().getConsoleSender().sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    @Override
    public void onDisable() {
        // Fancy shutdown messages
        getServer().getConsoleSender().sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        getServer().getConsoleSender().sendMessage("§c[CustomKit] §7Shutting down CustomKit Duels...");
        
        // Clean up all managers
        if (duelManager != null) {
            if (isDebugEnabled()) getLogger().info("Cleaning up active duels...");
            duelManager.cleanupAllDuels();
        }
        
        if (scoreboardManager != null) {
            if (isDebugEnabled()) getLogger().info("Cleaning up scoreboards...");
            scoreboardManager.cleanup();
        }
        
        if (healthDisplayManager != null) {
            if (isDebugEnabled()) getLogger().info("Cleaning up health displays...");
            healthDisplayManager.cleanup();
        }
        
        getServer().getConsoleSender().sendMessage("§c[CustomKit] §7Plugin disabled successfully!");
        getServer().getConsoleSender().sendMessage("§c[CustomKit] §7Thanks for using CustomKit! Made with love by §emoiz §c❤");
        getServer().getConsoleSender().sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    public static CustomKitDuels getInstance() {
        return instance;
    }
    
    public KitManager getKitManager() {
        return kitManager;
    }
    
    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public DuelManager getDuelManager() {
        return duelManager;
    }
    
    public CategoryManager getCategoryManager() {
        return categoryManager;
    }
    
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
    
    public HealthDisplayManager getHealthDisplayManager() {
        return healthDisplayManager;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
    
    public void reloadPluginConfig() {
        reloadConfig();
        arenaManager.loadArenas();
        categoryManager.reloadCategories();
        scoreboardManager.reloadConfig();
        menuManager.reloadMenus();
        getLogger().info("Configuration reloaded!");
    }
    
    public boolean isDebugEnabled() {
        return getConfig().getBoolean("settings.debug", false);
    }
    
    public int getRoundTransitionDelay() {
        return getConfig().getInt("settings.round-transition-delay", 2);
    }
    
    public int getDuelEndDelay() {
        return getConfig().getInt("settings.duel-end-delay", 2);
    }
}