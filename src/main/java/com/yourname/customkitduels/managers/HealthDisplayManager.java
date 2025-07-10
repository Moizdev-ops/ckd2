package com.yourname.customkitduels.managers;

import com.yourname.customkitduels.CustomKitDuels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced health display manager that shows health below player name tags
 * Uses scoreboard objectives to display health information below names
 */
public class HealthDisplayManager {
    
    private final CustomKitDuels plugin;
    private final Map<UUID, BukkitRunnable> healthTasks;
    private final Map<UUID, Scoreboard> originalScoreboards; // Store original scoreboards
    private final Map<UUID, Scoreboard> playerHealthScoreboards; // Individual scoreboards per player
    
    public HealthDisplayManager(CustomKitDuels plugin) {
        this.plugin = plugin;
        this.healthTasks = new HashMap<>();
        this.originalScoreboards = new HashMap<>();
        this.playerHealthScoreboards = new HashMap<>();
        
        plugin.getLogger().info("HealthDisplayManager initialized with below-name health display");
    }
    
    /**
     * Start health display for a player
     */
    public void startHealthDisplay(Player player) {
        stopHealthDisplay(player); // Stop any existing display
        
        // Store original scoreboard
        originalScoreboards.put(player.getUniqueId(), player.getScoreboard());
        
        // Create individual health scoreboard for this player
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            plugin.getLogger().severe("ScoreboardManager is null! Cannot create health display.");
            return;
        }
        
        Scoreboard healthScoreboard = manager.getNewScoreboard();
        
        // Create health objective that displays below name
        Objective healthObjective = healthScoreboard.registerNewObjective("health", "health", ChatColor.RED + "❤");
        healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        
        // Store the scoreboard for this player
        playerHealthScoreboards.put(player.getUniqueId(), healthScoreboard);
        
        // Set player to use health scoreboard
        player.setScoreboard(healthScoreboard);
        
        // Start health update task - FIXED: Update every 1 second
        BukkitRunnable healthTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.getDuelManager().isInAnyDuel(player)) {
                    this.cancel();
                    healthTasks.remove(player.getUniqueId());
                    return;
                }
                
                updateHealthDisplay(player);
            }
        };
        
        healthTask.runTaskTimer(plugin, 0L, 20L); // Update every 1 second (20 ticks)
        healthTasks.put(player.getUniqueId(), healthTask);
        
        // Initial update
        updateHealthDisplay(player);
    }
    
    /**
     * Stop health display for a player
     */
    public void stopHealthDisplay(Player player) {
        BukkitRunnable task = healthTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        
        // Remove player's health scoreboard
        playerHealthScoreboards.remove(player.getUniqueId());
        
        // Restore original scoreboard
        Scoreboard originalBoard = originalScoreboards.remove(player.getUniqueId());
        if (originalBoard != null) {
            player.setScoreboard(originalBoard);
        } else {
            // Fallback to main scoreboard
            org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }
    
    /**
     * OPTIMIZED: Update health display with minimal overhead
     */
    private void updateHealthDisplay(Player player) {
        Scoreboard healthScoreboard = playerHealthScoreboards.get(player.getUniqueId());
        if (healthScoreboard == null) {
            return;
        }
        
        Objective healthObjective = healthScoreboard.getObjective("health");
        if (healthObjective == null) {
            return;
        }
        
        try {
            // PERFORMANCE: Cache health calculations
            double health = player.getHealth();
            int displayHearts = (int) Math.round(health); // Direct conversion for performance
            
            // OPTIMIZED: Direct score access
            String playerName = player.getName();
            Score score = healthObjective.getScore(playerName);
            
            // PERFORMANCE: Only update if changed
            if (score.getScore() != displayHearts) {
                score.setScore(displayHearts);
            }
            
            // OPTIMIZED: Update opponent health efficiently
            var roundsDuel = plugin.getDuelManager().getRoundsDuel(player);
            if (roundsDuel != null) {
                Player opponent = roundsDuel.getOpponent(player);
                if (opponent != null && opponent.isOnline()) {
                    double opponentHealth = opponent.getHealth();
                    int opponentDisplayHearts = (int) Math.round(opponentHealth);
                    
                    String opponentName = opponent.getName();
                    Score opponentScore = healthObjective.getScore(opponentName);
                    
                    // PERFORMANCE: Only update if changed
                    if (opponentScore.getScore() != opponentDisplayHearts) {
                        opponentScore.setScore(opponentDisplayHearts);
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail for performance
        }
    }
    
    /**
     * Setup health display for all online players (used on plugin enable/reload)
     */
    public void setupHealthDisplayForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getDuelManager().isInAnyDuel(player)) {
                startHealthDisplay(player);
            }
        }
    }
    
    /**
     * Check if health display is active for a player
     */
    public boolean isHealthDisplayActive(Player player) {
        return healthTasks.containsKey(player.getUniqueId());
    }
    
    /**
     * Clean up all health displays
     */
    public void cleanup() {
        // Cancel all tasks
        for (BukkitRunnable task : healthTasks.values()) {
            task.cancel();
        }
        healthTasks.clear();
        
        // Restore all original scoreboards
        for (Map.Entry<UUID, Scoreboard> entry : originalScoreboards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.setScoreboard(entry.getValue());
            }
        }
        originalScoreboards.clear();
        playerHealthScoreboards.clear();
    }
}