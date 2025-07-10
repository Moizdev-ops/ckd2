package com.yourname.customkitduels.listeners;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.RoundsDuel;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {
    
    private final CustomKitDuels plugin;
    
    public PlayerListener(CustomKitDuels plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Reset player health to default (10 hearts = 20 health points)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                player.setHealth(20.0);
            } catch (Exception e) {
                // Silent fail
            }
        }, 20L); // Wait 1 second after join
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // OPTIMIZED: Early return for non-player entities to reduce overhead
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        
        // PERFORMANCE: Quick check if neither player is in a duel
        if (!plugin.getDuelManager().isInAnyDuel(victim) && !plugin.getDuelManager().isInAnyDuel(damager)) {
            return;
        }
        
        // Prevent damage during round transitions and duel end delays
        if (event.getEntity() instanceof Player) {
            // Check if victim is in transition (cannot be hit)
            if (plugin.getDuelManager().isPlayerInTransition(victim)) {
                event.setCancelled(true);
                return;
            }
        }
        
        if (event.getDamager() instanceof Player) {
            // Check if damager is in transition (cannot hit)
            if (plugin.getDuelManager().isPlayerInTransition(damager)) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        // Check if player is in duel and has natural regen disabled
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            Map<UUID, Boolean> playerNaturalRegenState = plugin.getDuelManager().getPlayerNaturalRegenState();
            Boolean hasNaturalRegen = playerNaturalRegenState.get(player.getUniqueId());
            
            // If natural regen is disabled for this player, cancel natural healing
            if (hasNaturalRegen != null && !hasNaturalRegen) {
                if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Stop health display
        plugin.getHealthDisplayManager().stopHealthDisplay(player);
        
        // Check if player is in a duel
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            // Get opponent before ending duel - make variables final for lambda
            final Player opponent;
            RoundsDuel roundsDuel = plugin.getDuelManager().getRoundsDuel(player);
            if (roundsDuel != null) {
                opponent = roundsDuel.getOpponent(player);
                
                // Award all remaining rounds to opponent
                if (opponent != null && opponent.isOnline()) {
                    int remainingRounds = roundsDuel.getTargetRounds() - Math.max(roundsDuel.getPlayer1Wins(), roundsDuel.getPlayer2Wins());
                    
                    // Set opponent as winner of all remaining rounds
                    if (opponent.equals(roundsDuel.getPlayer1())) {
                        while (roundsDuel.getPlayer1Wins() < roundsDuel.getTargetRounds()) {
                            roundsDuel.addWin(opponent);
                        }
                    } else {
                        while (roundsDuel.getPlayer2Wins() < roundsDuel.getTargetRounds()) {
                            roundsDuel.addWin(opponent);
                        }
                    }
                    
                    // Mark duel as inactive to prevent further processing
                    roundsDuel.setActive(false);
                    
                    // Remove from active duels
                    Map<UUID, RoundsDuel> activeRoundsDuels = plugin.getDuelManager().getActiveRoundsDuels();
                    activeRoundsDuels.remove(player.getUniqueId());
                    activeRoundsDuels.remove(opponent.getUniqueId());
                    
                    // Clean up opponent
                    plugin.getScoreboardManager().removeDuelScoreboard(opponent);
                    plugin.getHealthDisplayManager().stopHealthDisplay(opponent);
                    
                    // Send win message to opponent
                    opponent.sendMessage(ChatColor.GREEN + player.getName() + " disconnected! You win the duel!");
                    
                    // Restore opponent and teleport to spawn
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (opponent.isOnline()) {
                            // Restore opponent's health and inventory
                            restorePlayerQuick(opponent);
                            
                            // Teleport to spawn
                            Location spawn = plugin.getSpawnManager().getSpawn();
                            if (spawn != null) {
                                opponent.teleport(spawn);
                                opponent.sendMessage(ChatColor.GREEN + "You have been teleported to spawn.");
                            } else {
                                opponent.teleport(opponent.getWorld().getSpawnLocation());
                            }
                        }
                    }, 20L); // 1 second delay
                }
            }
        }
    }
    
    private void restorePlayerQuick(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        
        // Remove potion effects
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        // Reset health to default
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        } catch (Exception e) {
            player.setHealth(Math.min(20.0, player.getMaxHealth()));
        }
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Set gamemode
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        
        player.updateInventory();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            // Clear drops to prevent item loss
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // Keep player at death location - prevent any teleportation
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            
            // Cancel death message
            event.setDeathMessage(null);
            
            // End the duel round
            plugin.getDuelManager().endDuel(player, true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // FIXED: Cancel respawn completely if player is in duel to prevent world spawn teleportation
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            // Cancel the respawn event to prevent any teleportation
            event.setCancelled(true);
            
            // Keep player alive at current location
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.getDuelManager().isInAnyDuel(player)) {
                    // Set health to 1 to keep player alive but show they "died"
                    player.setHealth(1.0);
                    
                    // Add temporary invulnerability to prevent immediate re-death
                    player.setNoDamageTicks(40); // 2 seconds of invulnerability
                }
            });
        }
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            // Check rounds duel
            RoundsDuel roundsDuel = plugin.getDuelManager().getRoundsDuel(player);
            if (roundsDuel != null) {
                // Check if teleporting outside arena bounds
                if (!isInArena(event.getTo(), roundsDuel.getArena())) {
                    // Only cancel if it's not a plugin-initiated teleport
                    if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "You cannot teleport during a rounds duel!");
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        
        if (plugin.getDuelManager().isInAnyDuel(player)) {
            // Block certain commands during duels
            if (command.startsWith("/tp") || command.startsWith("/teleport") ||
                command.startsWith("/home") || command.startsWith("/spawn") ||
                command.startsWith("/warp") || command.startsWith("/back")) {
                
                // Allow ckd commands
                if (!command.startsWith("/ckd")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot use that command during a duel!");
                }
            }
        }
    }
    
    private boolean isInArena(org.bukkit.Location location, com.yourname.customkitduels.data.Arena arena) {
        if (arena.getPos1() == null || arena.getPos2() == null) {
            return false;
        }
        
        org.bukkit.Location pos1 = arena.getPos1();
        org.bukkit.Location pos2 = arena.getPos2();
        
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        return location.getX() >= minX && location.getX() <= maxX &&
               location.getY() >= minY && location.getY() <= maxY &&
               location.getZ() >= minZ && location.getZ() <= maxZ;
    }
}