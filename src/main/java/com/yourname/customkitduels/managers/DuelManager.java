package com.yourname.customkitduels.managers;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.*;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DuelManager {
    
    private final CustomKitDuels plugin;
    private final Map<UUID, RoundsDuelRequest> pendingRoundsRequests;
    private final Map<UUID, RoundsDuel> activeRoundsDuels;
    private final Map<UUID, Location> savedLocations;
    private final Set<UUID> playersInCountdown;
    private final Map<UUID, BukkitRunnable> arenaBoundsCheckers;
    private final Map<UUID, Boolean> playerNaturalRegenState; // Store original natural regen state per player
    private final Set<UUID> playersInTransition; // Players in round transition (cannot be hit)
    
    public DuelManager(CustomKitDuels plugin) {
        this.plugin = plugin;
        this.pendingRoundsRequests = new HashMap<>();
        this.activeRoundsDuels = new HashMap<>();
        this.savedLocations = new HashMap<>();
        this.playersInCountdown = new HashSet<>();
        this.arenaBoundsCheckers = new HashMap<>();
        this.playerNaturalRegenState = new HashMap<>();
        this.playersInTransition = new HashSet<>();
    }
    
    public void sendRoundsDuelRequest(Player challenger, Player target, Kit kit, int targetRounds) {
        // Check if players are already in duels
        if (isInAnyDuel(challenger)) {
            challenger.sendMessage(ChatColor.RED + "You are already in a duel or countdown!");
            return;
        }
        
        if (isInAnyDuel(target)) {
            challenger.sendMessage(ChatColor.RED + "That player is already in a duel or countdown!");
            return;
        }
        
        // Check if target has pending request
        if (pendingRoundsRequests.containsKey(target.getUniqueId())) {
            challenger.sendMessage(ChatColor.RED + "That player already has a pending duel request!");
            return;
        }
        
        // Check if arena is available
        Arena arena = plugin.getArenaManager().getRandomAvailableArena();
        if (arena == null) {
            challenger.sendMessage(ChatColor.RED + "No arenas are available for dueling!");
            return;
        }
        
        // Create rounds duel request
        RoundsDuelRequest request = new RoundsDuelRequest(challenger, target, kit, arena, targetRounds);
        pendingRoundsRequests.put(target.getUniqueId(), request);
        
        // Send enhanced messages with clickable accept button
        challenger.sendMessage(ChatColor.GREEN + "Rounds duel request sent to " + target.getName() + " with kit '" + kit.getName() + "' (First to " + targetRounds + ")!");
        
        // Send formatted message to target
        target.sendMessage(ChatColor.YELLOW + challenger.getName() + " has sent you a custom kit duel.");
        target.sendMessage(ChatColor.YELLOW + "Rounds: " + ChatColor.WHITE + targetRounds);
        
        // Create clickable accept button
        TextComponent acceptButton = new TextComponent("[Click to Accept]");
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/customkit accept-internal " + challenger.getUniqueId()));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("Click to accept the duel").color(net.md_5.bungee.api.ChatColor.GREEN).create()));
        
        target.spigot().sendMessage(acceptButton);
        
        // Auto-expire request after 30 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingRoundsRequests.remove(target.getUniqueId()) != null) {
                challenger.sendMessage(ChatColor.RED + "Your rounds duel request to " + target.getName() + " has expired.");
                target.sendMessage(ChatColor.RED + "The rounds duel request from " + challenger.getName() + " has expired.");
            }
        }, 600L); // 30 seconds
    }
    
    public void acceptRoundsDuel(Player target) {
        RoundsDuelRequest request = pendingRoundsRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(ChatColor.RED + "You don't have any pending duel requests!");
            return;
        }
        
        Player challenger = request.getChallenger();
        if (!challenger.isOnline()) {
            target.sendMessage(ChatColor.RED + "The challenger is no longer online!");
            return;
        }
        
        // Check if players are still available
        if (isInAnyDuel(challenger) || isInAnyDuel(target)) {
            target.sendMessage(ChatColor.RED + "One of the players is already in a duel or countdown!");
            return;
        }
        
        startRoundsCountdown(challenger, target, request.getKit(), request.getArena(), request.getTargetRounds());
    }
    
    private void startRoundsCountdown(Player challenger, Player target, Kit kit, Arena arena, int targetRounds) {
        // Add players to countdown set
        playersInCountdown.add(challenger.getUniqueId());
        playersInCountdown.add(target.getUniqueId());
        
        // Save current locations
        savedLocations.put(challenger.getUniqueId(), challenger.getLocation());
        savedLocations.put(target.getUniqueId(), target.getLocation());
        
        // FIXED: Regenerate arena at START of duel
        if (arena.hasRegeneration()) {
            plugin.getArenaManager().regenerateArena(arena);
        }
        
        // Give kit immediately after accepting so players can organize during countdown
        preparePlayer(challenger, kit, challenger.getUniqueId());
        preparePlayer(target, kit, challenger.getUniqueId());
        
        // Teleport players to arena spawn points
        challenger.teleport(arena.getSpawn1());
        target.teleport(arena.getSpawn2());
        
        // Send initial message
        challenger.sendMessage(ChatColor.GREEN + "Rounds duel accepted! First to " + targetRounds + " rounds wins!");
        target.sendMessage(ChatColor.GREEN + "Rounds duel accepted! First to " + targetRounds + " rounds wins!");
        challenger.sendMessage(ChatColor.YELLOW + "Use the 5-second countdown to organize your inventory!");
        target.sendMessage(ChatColor.YELLOW + "Use the 5-second countdown to organize your inventory!");
        
        // Show scoreboard during countdown
        RoundsDuel tempRoundsDuel = new RoundsDuel(challenger, target, kit, arena, targetRounds);
        plugin.getScoreboardManager().showDuelScoreboard(challenger, tempRoundsDuel);
        plugin.getScoreboardManager().showDuelScoreboard(target, tempRoundsDuel);
        
        // Start countdown
        new BukkitRunnable() {
            int countdown = 5;
            
            @Override
            public void run() {
                // Check if players are still online and in countdown
                if (!challenger.isOnline() || !target.isOnline() ||
                    !playersInCountdown.contains(challenger.getUniqueId()) ||
                    !playersInCountdown.contains(target.getUniqueId())) {
                    
                    // Cancel countdown
                    playersInCountdown.remove(challenger.getUniqueId());
                    playersInCountdown.remove(target.getUniqueId());
                    
                    if (challenger.isOnline()) {
                        challenger.sendMessage(ChatColor.RED + "Duel cancelled - player disconnected!");
                        restorePlayer(challenger);
                    }
                    if (target.isOnline()) {
                        target.sendMessage(ChatColor.RED + "Duel cancelled - player disconnected!");
                        restorePlayer(target);
                    }
                    
                    this.cancel();
                    return;
                }
                
                if (countdown > 0) {
                    // Send countdown message with proper title
                    String countdownText = ChatColor.RED + "" + ChatColor.BOLD + countdown;
                    String subtitle = ChatColor.YELLOW + "Round 1 starting in " + countdown + "...";
                    
                    challenger.sendTitle(countdownText, subtitle, 0, 20, 0);
                    target.sendTitle(countdownText, subtitle, 0, 20, 0);
                    
                    // Play note block sound
                    challenger.playSound(challenger.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    
                    countdown--;
                } else {
                    // Start the rounds duel
                    String fightTitle = ChatColor.GREEN + "" + ChatColor.BOLD + "FIGHT!";
                    String fightSubtitle = ChatColor.YELLOW + "Round 1 - First to " + targetRounds + "!";
                    
                    challenger.sendTitle(fightTitle, fightSubtitle, 0, 40, 10);
                    target.sendTitle(fightTitle, fightSubtitle, 0, 40, 10);
                    
                    // Play start sound
                    challenger.playSound(challenger.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    
                    // Remove from countdown and start actual rounds duel
                    playersInCountdown.remove(challenger.getUniqueId());
                    playersInCountdown.remove(target.getUniqueId());
                    
                    startRoundsDuel(challenger, target, kit, arena, targetRounds);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second
    }
    
    private void startRoundsDuel(Player challenger, Player target, Kit kit, Arena arena, int targetRounds) {
        // Create rounds duel
        RoundsDuel roundsDuel = new RoundsDuel(challenger, target, kit, arena, targetRounds);
        activeRoundsDuels.put(challenger.getUniqueId(), roundsDuel);
        activeRoundsDuels.put(target.getUniqueId(), roundsDuel);
        
        // Players already have kit from countdown, just refresh health/effects
        refreshPlayerForDuel(challenger, kit, challenger.getUniqueId());
        refreshPlayerForDuel(target, kit, challenger.getUniqueId());
        
        // Start arena bounds checking - OPTIMIZED: Check every 2 seconds
        startArenaBoundsChecking(challenger, arena);
        startArenaBoundsChecking(target, arena);
        
        // Start health display if enabled
        if (plugin.getKitManager().getKitHealthIndicators(challenger.getUniqueId(), kit.getName())) {
            plugin.getHealthDisplayManager().startHealthDisplay(challenger);
            plugin.getHealthDisplayManager().startHealthDisplay(target);
        }
        
        // Show scoreboard
        plugin.getScoreboardManager().showDuelScoreboard(challenger, roundsDuel);
        plugin.getScoreboardManager().showDuelScoreboard(target, roundsDuel);
    }
    
    // New method to refresh player without giving kit again
    private void refreshPlayerForDuel(Player player, Kit kit, UUID kitOwnerUUID) {
        // Get kit settings from the kit owner (challenger)
        double kitHearts = plugin.getKitManager().getKitHearts(kitOwnerUUID, kit.getName());
        boolean naturalRegen = plugin.getKitManager().getKitNaturalRegen(kitOwnerUUID, kit.getName());
        
        // Set health based on kit settings (convert hearts to health points)
        double maxHealth = kitHearts * 2.0; // 1 heart = 2 health points
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            player.setHealth(maxHealth);
        } catch (Exception e) {
            player.setHealth(Math.min(maxHealth, player.getMaxHealth()));
        }
        
        // Set hunger
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Handle natural health regeneration setting per player
        if (!naturalRegen) {
            playerNaturalRegenState.put(player.getUniqueId(), false);
        } else {
            playerNaturalRegenState.put(player.getUniqueId(), true);
        }
        
        // Set gamemode
        player.setGameMode(GameMode.SURVIVAL);
    }
    
    private void startArenaBoundsChecking(Player player, Arena arena) {
        BukkitRunnable boundsChecker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isInAnyDuel(player)) {
                    this.cancel();
                    arenaBoundsCheckers.remove(player.getUniqueId());
                    return;
                }
                
                if (!isPlayerInArena(player, arena)) {
                    // Player is outside arena bounds - teleport back to spawn
                    RoundsDuel roundsDuel = activeRoundsDuels.get(player.getUniqueId());
                    if (roundsDuel != null) {
                        Location spawnPoint = player.equals(roundsDuel.getPlayer1()) ? 
                            arena.getSpawn1() : arena.getSpawn2();
                        
                        // FIXED: Safe teleport to prevent fall damage
                        safeTeleportToArena(player, spawnPoint);
                        player.sendMessage(ChatColor.RED + "You cannot leave the arena during a duel!");
                    }
                }
            }
        };
        
        // OPTIMIZED: Check every 1 second for better responsiveness
        boundsChecker.runTaskTimer(plugin, 20L, 20L);
        arenaBoundsCheckers.put(player.getUniqueId(), boundsChecker);
    }
    
    /**
     * OPTIMIZED: Safe teleport that prevents fall damage and provides smooth experience
     */
    private void safeTeleportToArena(Player player, Location spawnPoint) {
        // Store current velocity to prevent momentum issues
        org.bukkit.util.Vector velocity = player.getVelocity();
        
        // Create safe spawn location (slightly above ground to prevent suffocation)
        Location safeSpawn = spawnPoint.clone();
        safeSpawn.setY(safeSpawn.getY() + 0.5); // Slightly above ground
        
        // Teleport player
        player.teleport(safeSpawn);
        
        // FIXED: Prevent fall damage and reset velocity
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFallDistance(0);
        player.setNoDamageTicks(20); // 1 second of invulnerability
        
        // Play teleport sound
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
    }
    
    public void endDuel(Player player, boolean died) {
        // Stop arena bounds checking
        BukkitRunnable boundsChecker = arenaBoundsCheckers.remove(player.getUniqueId());
        if (boundsChecker != null) {
            boundsChecker.cancel();
        }
        
        // Stop health display
        plugin.getHealthDisplayManager().stopHealthDisplay(player);
        
        // Handle rounds duel
        RoundsDuel roundsDuel = activeRoundsDuels.get(player.getUniqueId());
        if (roundsDuel != null) {
            // Add player to transition state to prevent damage
            playersInTransition.add(player.getUniqueId());
            Player opponent = roundsDuel.getOpponent(player);
            if (opponent != null) {
                playersInTransition.add(opponent.getUniqueId());
            }
            
            endRoundsDuelRound(player, died);
            return;
        }
    }
    
    private void endRoundsDuelRound(Player player, boolean died) {
        RoundsDuel roundsDuel = activeRoundsDuels.get(player.getUniqueId());
        if (roundsDuel == null || !roundsDuel.isActive()) return;
        
        Player opponent = roundsDuel.getOpponent(player);
        Player roundWinner = died ? opponent : player;
        Player roundLoser = died ? player : opponent;
        
        // Add win to the winner
        roundsDuel.addWin(roundWinner);
        
        // Show win/loss titles with configurable messages
        showRoundResultTitles(roundWinner, roundLoser, roundsDuel);
        
        // Update scoreboards
        plugin.getScoreboardManager().updateDuelScoreboard(roundsDuel.getPlayer1(), roundsDuel);
        plugin.getScoreboardManager().updateDuelScoreboard(roundsDuel.getPlayer2(), roundsDuel);
        
        // Check if duel is complete
        if (roundsDuel.isComplete()) {
            // End the entire rounds duel
            Player overallWinner = roundsDuel.getOverallWinner();
            Player overallLoser = overallWinner.equals(roundsDuel.getPlayer1()) ? roundsDuel.getPlayer2() : roundsDuel.getPlayer1();
            
            // Remove from active duels
            activeRoundsDuels.remove(roundsDuel.getPlayer1().getUniqueId());
            activeRoundsDuels.remove(roundsDuel.getPlayer2().getUniqueId());
            
            // Stop arena bounds checking and health display
            BukkitRunnable boundsChecker1 = arenaBoundsCheckers.remove(roundsDuel.getPlayer1().getUniqueId());
            if (boundsChecker1 != null) boundsChecker1.cancel();
            BukkitRunnable boundsChecker2 = arenaBoundsCheckers.remove(roundsDuel.getPlayer2().getUniqueId());
            if (boundsChecker2 != null) boundsChecker2.cancel();
            
            plugin.getHealthDisplayManager().stopHealthDisplay(roundsDuel.getPlayer1());
            plugin.getHealthDisplayManager().stopHealthDisplay(roundsDuel.getPlayer2());
            
            // Remove scoreboards
            plugin.getScoreboardManager().removeDuelScoreboard(roundsDuel.getPlayer1());
            plugin.getScoreboardManager().removeDuelScoreboard(roundsDuel.getPlayer2());
            
            // Send win message only to winner
            overallWinner.sendMessage(ChatColor.GREEN + "You won the duel!");
            overallLoser.sendMessage(ChatColor.RED + "You lost the duel!");
            
            // FIXED: Regenerate arena at END of duel
            if (roundsDuel.getArena().hasRegeneration()) {
                plugin.getArenaManager().regenerateArena(roundsDuel.getArena());
            }
            
            // Get delay from config
            int duelEndDelay = plugin.getDuelEndDelay();
            
            // Restore players after configured delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Remove from transition state
                playersInTransition.remove(roundsDuel.getPlayer1().getUniqueId());
                playersInTransition.remove(roundsDuel.getPlayer2().getUniqueId());
                
                restorePlayer(roundsDuel.getPlayer1());
                restorePlayer(roundsDuel.getPlayer2());
            }, duelEndDelay * 20L);
        } else {
            // Get delay from config
            int roundTransitionDelay = plugin.getRoundTransitionDelay();
            
            // Start next round after configured delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Remove from transition state
                playersInTransition.remove(roundWinner.getUniqueId());
                playersInTransition.remove(roundLoser.getUniqueId());
                
                if (roundsDuel.isActive() && roundWinner.isOnline() && roundLoser.isOnline()) {
                    startNextRound(roundsDuel);
                } else {
                    // End duel if someone disconnected
                    activeRoundsDuels.remove(roundsDuel.getPlayer1().getUniqueId());
                    activeRoundsDuels.remove(roundsDuel.getPlayer2().getUniqueId());
                    plugin.getScoreboardManager().removeDuelScoreboard(roundsDuel.getPlayer1());
                    plugin.getScoreboardManager().removeDuelScoreboard(roundsDuel.getPlayer2());
                    if (roundsDuel.getPlayer1().isOnline()) restorePlayer(roundsDuel.getPlayer1());
                    if (roundsDuel.getPlayer2().isOnline()) restorePlayer(roundsDuel.getPlayer2());
                }
            }, roundTransitionDelay * 20L);
        }
    }
    
    private void showRoundResultTitles(Player winner, Player loser, RoundsDuel roundsDuel) {
        // Get scores for display
        int winnerScore = winner.equals(roundsDuel.getPlayer1()) ? roundsDuel.getPlayer1Wins() : roundsDuel.getPlayer2Wins();
        int loserScore = winner.equals(roundsDuel.getPlayer1()) ? roundsDuel.getPlayer2Wins() : roundsDuel.getPlayer1Wins();
        String scoreText = winnerScore + "-" + loserScore;
        
        // Get configurable messages
        String winTitle = plugin.getConfig().getString("messages.round-win-title", "&9Won!");
        String loseTitle = plugin.getConfig().getString("messages.round-lose-title", "&cLost!");
        String scoreSubtitle = plugin.getConfig().getString("messages.round-score-subtitle", "&7Score: &f{score}");
        
        // Replace placeholders and translate colors
        winTitle = ChatColor.translateAlternateColorCodes('&', winTitle);
        loseTitle = ChatColor.translateAlternateColorCodes('&', loseTitle);
        scoreSubtitle = ChatColor.translateAlternateColorCodes('&', scoreSubtitle.replace("{score}", scoreText));
        
        // Show titles
        winner.sendTitle(winTitle, scoreSubtitle, 10, 40, 10);
        loser.sendTitle(loseTitle, scoreSubtitle, 10, 40, 10);
    }
    
    private void startNextRound(RoundsDuel roundsDuel) {
        Player player1 = roundsDuel.getPlayer1();
        Player player2 = roundsDuel.getPlayer2();
        
        if (!player1.isOnline() || !player2.isOnline()) {
            // End duel if someone disconnected
            activeRoundsDuels.remove(player1.getUniqueId());
            activeRoundsDuels.remove(player2.getUniqueId());
            plugin.getScoreboardManager().removeDuelScoreboard(player1);
            plugin.getScoreboardManager().removeDuelScoreboard(player2);
            if (player1.isOnline()) restorePlayer(player1);
            if (player2.isOnline()) restorePlayer(player2);
            return;
        }
        
        // Regenerate arena between rounds if enabled
        if (roundsDuel.getArena().hasRegeneration()) {
            plugin.getArenaManager().regenerateArena(roundsDuel.getArena());
        }
        
        // Teleport players back to spawn points
        player1.teleport(roundsDuel.getArena().getSpawn1());
        player2.teleport(roundsDuel.getArena().getSpawn2());
        
        // FIXED: Clear inventories and give fresh kit for next round
        clearAndPreparePlayer(player1, roundsDuel.getKit(), roundsDuel.getPlayer1().getUniqueId());
        clearAndPreparePlayer(player2, roundsDuel.getKit(), roundsDuel.getPlayer1().getUniqueId());
        
        // Restart health display if enabled
        if (plugin.getKitManager().getKitHealthIndicators(roundsDuel.getPlayer1().getUniqueId(), roundsDuel.getKit().getName())) {
            plugin.getHealthDisplayManager().startHealthDisplay(player1);
            plugin.getHealthDisplayManager().startHealthDisplay(player2);
        }
        
        // Update scoreboards
        plugin.getScoreboardManager().updateDuelScoreboard(player1, roundsDuel);
        plugin.getScoreboardManager().updateDuelScoreboard(player2, roundsDuel);
        
        // Send round start messages
        String roundMessage = ChatColor.GREEN + "Round " + roundsDuel.getCurrentRound() + " starting!";
        String progressMessage = ChatColor.AQUA + roundsDuel.getProgressString();
        
        player1.sendMessage(roundMessage);
        player1.sendMessage(progressMessage);
        player2.sendMessage(roundMessage);
        player2.sendMessage(progressMessage);
        
        // Start countdown for next round with 5 seconds for inventory organization
        new BukkitRunnable() {
            int countdown = 5;
            
            @Override
            public void run() {
                if (!player1.isOnline() || !player2.isOnline() || !roundsDuel.isActive()) {
                    this.cancel();
                    return;
                }
                
                if (countdown > 0) {
                    String countdownText = ChatColor.RED + "" + ChatColor.BOLD + countdown;
                    String subtitle = ChatColor.YELLOW + "Round " + roundsDuel.getCurrentRound() + " in " + countdown + "...";
                    
                    player1.sendTitle(countdownText, subtitle, 0, 20, 0);
                    player2.sendTitle(countdownText, subtitle, 0, 20, 0);
                    
                    // Play note block sound
                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    
                    countdown--;
                } else {
                    String fightTitle = ChatColor.GREEN + "" + ChatColor.BOLD + "FIGHT!";
                    String fightSubtitle = ChatColor.YELLOW + "Round " + roundsDuel.getCurrentRound() + "!";
                    
                    player1.sendTitle(fightTitle, fightSubtitle, 0, 40, 10);
                    player2.sendTitle(fightTitle, fightSubtitle, 0, 40, 10);
                    
                    // Play start sound
                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    /**
     * OPTIMIZED: Clear inventory and give fresh kit for new round
     */
    private void clearAndPreparePlayer(Player player, Kit kit, UUID kitOwnerUUID) {
        // PERFORMANCE: Clear everything completely in one batch operation
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        
        // Clear cursor item
        if (player.getItemOnCursor() != null && player.getItemOnCursor().getType() != Material.AIR) {
            player.setItemOnCursor(null);
        }
        
        // Remove all potion effects efficiently
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        
        // Get kit settings from the kit owner (challenger)
        double kitHearts = plugin.getKitManager().getKitHearts(kitOwnerUUID, kit.getName());
        boolean naturalRegen = plugin.getKitManager().getKitNaturalRegen(kitOwnerUUID, kit.getName());
        
        // Set health based on kit settings (convert hearts to health points)
        double maxHealth = kitHearts * 2.0; // 1 heart = 2 health points
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            player.setHealth(maxHealth);
        } catch (Exception e) {
            player.setHealth(Math.min(maxHealth, player.getMaxHealth()));
        }
        
        // Set hunger and saturation
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Reset experience
        player.setExp(0);
        player.setLevel(0);
        
        // Handle natural health regeneration setting per player
        playerNaturalRegenState.put(player.getUniqueId(), naturalRegen);
        
        // Set gamemode
        player.setGameMode(GameMode.SURVIVAL);
        
        // Give kit - handle main inventory (36 slots)
        ItemStack[] contents = kit.getContents();
        if (contents != null) {
            // Set main inventory (slots 0-35)
            ItemStack[] mainInventory = new ItemStack[36];
            System.arraycopy(contents, 0, mainInventory, 0, Math.min(contents.length, 36));
            player.getInventory().setContents(mainInventory);
            
            // Set offhand (slot 36 in our extended array)
            if (contents.length > 36 && contents[36] != null) {
                player.getInventory().setItemInOffHand(contents[36]);
            }
        }
        
        // Give armor
        if (kit.getArmor() != null) {
            player.getInventory().setArmorContents(kit.getArmor().clone());
        }
        
        // PERFORMANCE: Single inventory update at the end
        player.updateInventory();
    }
    
    private void restorePlayer(Player player) {
        // Stop health display
        plugin.getHealthDisplayManager().stopHealthDisplay(player);
        
        // Clear inventory completely
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        
        // Remove all potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        // Clear any remaining items from cursor
        if (player.getItemOnCursor() != null && player.getItemOnCursor().getType() != Material.AIR) {
            player.setItemOnCursor(null);
        }
        
        // Clean up natural regen state
        playerNaturalRegenState.remove(player.getUniqueId());
        
        // Reset health to exactly 10 hearts (20 health points)
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        } catch (Exception e) {
            // Fallback if attribute access fails
            player.setHealth(Math.min(20.0, player.getMaxHealth()));
        }
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Reset experience
        player.setExp(0);
        player.setLevel(0);
        
        // Set gamemode
        player.setGameMode(GameMode.SURVIVAL);
        
        // Teleport back to spawn
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(spawn);
                    player.sendMessage(ChatColor.GREEN + "You have been teleported to spawn.");
                }
            }, 40L); // 2 second delay
        } else {
            // Fallback to saved location or world spawn
            Location savedLocation = savedLocations.remove(player.getUniqueId());
            if (savedLocation != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(savedLocation);
                    }
                }, 40L);
            } else {
                // Fallback to world spawn
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(player.getWorld().getSpawnLocation());
                    }
                }, 40L);
            }
        }
        
        // Force inventory update
        player.updateInventory();
    }
    
    private void preparePlayer(Player player, Kit kit, UUID kitOwnerUUID) {
        // Store original inventory before clearing
        savedLocations.put(player.getUniqueId(), player.getLocation());
        
        // Clear player inventory completely
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        
        // Clear all potion effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        
        // Get kit settings from the kit owner (challenger)
        double kitHearts = plugin.getKitManager().getKitHearts(kitOwnerUUID, kit.getName());
        boolean naturalRegen = plugin.getKitManager().getKitNaturalRegen(kitOwnerUUID, kit.getName());
        
        // Set health based on kit settings (convert hearts to health points)
        double maxHealth = kitHearts * 2.0; // 1 heart = 2 health points
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            player.setHealth(maxHealth);
        } catch (Exception e) {
            // Fallback if attribute access fails
            player.setHealth(Math.min(maxHealth, player.getMaxHealth()));
        }
        
        // Set hunger
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Handle natural health regeneration setting per player
        if (!naturalRegen) {
            playerNaturalRegenState.put(player.getUniqueId(), false);
        } else {
            playerNaturalRegenState.put(player.getUniqueId(), true);
        }
        
        // Set gamemode
        player.setGameMode(GameMode.SURVIVAL);
        
        // Give kit - handle main inventory (36 slots)
        ItemStack[] contents = kit.getContents();
        if (contents != null) {
            // Set main inventory (slots 0-35)
            ItemStack[] mainInventory = new ItemStack[36];
            System.arraycopy(contents, 0, mainInventory, 0, Math.min(contents.length, 36));
            player.getInventory().setContents(mainInventory);
            
            // Set offhand (slot 36 in our extended array)
            if (contents.length > 36 && contents[36] != null) {
                player.getInventory().setItemInOffHand(contents[36]);
            }
        }
        
        // Give armor
        if (kit.getArmor() != null) {
            player.getInventory().setArmorContents(kit.getArmor().clone());
        }
        
        // Update inventory
        player.updateInventory();
    }
    
    public boolean isInRoundsDuel(Player player) {
        return activeRoundsDuels.containsKey(player.getUniqueId());
    }
    
    public boolean isInAnyDuel(Player player) {
        return isInRoundsDuel(player) || playersInCountdown.contains(player.getUniqueId());
    }
    
    public RoundsDuel getRoundsDuel(Player player) {
        return activeRoundsDuels.get(player.getUniqueId());
    }
    
    private boolean isPlayerInArena(Player player, Arena arena) {
        if (arena.getPos1() == null || arena.getPos2() == null) {
            return false;
        }
        
        Location loc = player.getLocation();
        Location pos1 = arena.getPos1();
        Location pos2 = arena.getPos2();
        
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getY() >= minY && loc.getY() <= maxY &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
    
    public void cleanupAllDuels() {
        // Cancel all arena bounds checkers
        for (BukkitRunnable checker : arenaBoundsCheckers.values()) {
            checker.cancel();
        }
        arenaBoundsCheckers.clear();
        
        // End all active rounds duels
        for (UUID playerId : new ArrayList<>(activeRoundsDuels.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                RoundsDuel roundsDuel = activeRoundsDuels.get(playerId);
                if (roundsDuel != null) {
                    roundsDuel.setActive(false);
                    plugin.getScoreboardManager().removeDuelScoreboard(player);
                    restorePlayer(player);
                }
            }
        }
        
        // Clear countdown players
        for (UUID playerId : new ArrayList<>(playersInCountdown)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                restorePlayer(player);
            }
        }
        
        // Clear all data
        activeRoundsDuels.clear();
        pendingRoundsRequests.clear();
        savedLocations.clear();
        playersInCountdown.clear();
        playerNaturalRegenState.clear();
        playersInTransition.clear();
    }
    
    public void leaveDuel(Player player) {
        if (isInRoundsDuel(player)) {
            RoundsDuel roundsDuel = activeRoundsDuels.get(player.getUniqueId());
            if (roundsDuel != null) {
                Player opponent = roundsDuel.getOpponent(player);
                
                // Award all remaining rounds to opponent
                if (opponent != null && opponent.isOnline()) {
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
                    
                    opponent.sendMessage(ChatColor.GREEN + player.getName() + " left the duel! You win!");
                }
                
                // Mark duel as inactive
                roundsDuel.setActive(false);
                
                // Remove from active duels
                activeRoundsDuels.remove(player.getUniqueId());
                if (opponent != null) {
                    activeRoundsDuels.remove(opponent.getUniqueId());
                }
                
                // Clean up
                plugin.getScoreboardManager().removeDuelScoreboard(player);
                if (opponent != null) {
                    plugin.getScoreboardManager().removeDuelScoreboard(opponent);
                }
                
                // Stop bounds checking
                BukkitRunnable boundsChecker1 = arenaBoundsCheckers.remove(player.getUniqueId());
                if (boundsChecker1 != null) boundsChecker1.cancel();
                if (opponent != null) {
                    BukkitRunnable boundsChecker2 = arenaBoundsCheckers.remove(opponent.getUniqueId());
                    if (boundsChecker2 != null) boundsChecker2.cancel();
                }
                
                // Restore players
                restorePlayer(player);
                if (opponent != null && opponent.isOnline()) {
                    restorePlayer(opponent);
                }
                
                player.sendMessage(ChatColor.YELLOW + "You left the duel.");
            }
        } else {
            // Remove from countdown if in countdown
            if (playersInCountdown.contains(player.getUniqueId())) {
                playersInCountdown.remove(player.getUniqueId());
                restorePlayer(player);
                player.sendMessage(ChatColor.YELLOW + "You left the duel countdown.");
            }
        }
    }
    
    // Add getter methods for PlayerListener access
    public Map<UUID, RoundsDuel> getActiveRoundsDuels() {
        return activeRoundsDuels;
    }
    
    public Map<UUID, Boolean> getPlayerNaturalRegenState() {
        return playerNaturalRegenState;
    }
    
    public boolean isPlayerInTransition(Player player) {
        return playersInTransition.contains(player.getUniqueId());
    }
}