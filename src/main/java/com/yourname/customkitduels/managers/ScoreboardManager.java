package com.yourname.customkitduels.managers;

import com.yourname.customkitduels.CustomKitDuels;
import com.yourname.customkitduels.data.RoundsDuel;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced ScoreboardManager using FastBoard with proper color support
 * Provides high-performance scoreboards with hex color support
 */
public class ScoreboardManager {
    
    private final CustomKitDuels plugin;
    private final File scoreboardFile;
    private FileConfiguration scoreboardConfig;
    private final Map<UUID, FastBoard> playerBoards;
    private final Map<UUID, BukkitRunnable> updateTasks;
    
    // Color patterns for proper hex color support
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>");
    
    // Cache for duration calculations to reduce overhead
    private final Map<UUID, Long> lastDurationUpdate = new HashMap<>();
    private final Map<UUID, String> cachedDuration = new HashMap<>();
    
    // Cache for color translations to reduce repeated regex operations
    private final Map<String, String> colorCache = new HashMap<>();
    
    public ScoreboardManager(CustomKitDuels plugin) {
        this.plugin = plugin;
        this.scoreboardFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        this.playerBoards = new HashMap<>();
        this.updateTasks = new HashMap<>();
        
        loadScoreboardConfig();
        plugin.getLogger().info("ScoreboardManager initialized with FastBoard and proper color support");
    }
    
    private void loadScoreboardConfig() {
        if (!scoreboardFile.exists()) {
            createDefaultScoreboardConfig();
        }
        
        scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardFile);
    }
    
    private void createDefaultScoreboardConfig() {
        scoreboardConfig = new YamlConfiguration();
        
        // Using proper color codes that will be translated
        scoreboardConfig.set("title", "&#00FF98&lPakMC");
        scoreboardConfig.set("lines", Arrays.asList(
            " ",
            " &#00FF98&lDUEL &7(FT&#C3F6E2<rounds>&7)",
            " &#C3F6E2│ Duration: &#00FF98<duration>",
            " &#C3F6E2│ Round: &#00FF98<current_round>",
            " ",
            " &#00FF98&lSCORE",
            " &#C3F6E2│ &a<player_score> &7- &c<opponent_score>",
            " &#C3F6E2│ &7<player_name> vs <opponent_name>",
            " ",
            "    &#C3F6E2pakmc.xyz"
        ));
        
        try {
            scoreboardConfig.save(scoreboardFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create scoreboard.yml: " + e.getMessage());
        }
    }
    
    public void showDuelScoreboard(Player player, RoundsDuel roundsDuel) {
        // Remove existing board if present
        removeDuelScoreboard(player);
        
        // Get title and translate colors
        String title = scoreboardConfig.getString("title", "&#00FF98&lPakMC");
        String translatedTitle = translateColors(title);
        
        // Create FastBoard
        FastBoard board = new FastBoard(player);
        board.updateTitle(translatedTitle);
        
        playerBoards.put(player.getUniqueId(), board);
        
        // Start update task for real-time updates - FIXED: Update every 1 second
        BukkitRunnable updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playerBoards.containsKey(player.getUniqueId()) || !roundsDuel.isActive()) {
                    this.cancel();
                    updateTasks.remove(player.getUniqueId());
                    return;
                }
                
                updateDuelScoreboard(player, roundsDuel);
            }
        };
        
        updateTask.runTaskTimer(plugin, 0L, 20L); // Update every 1 second (20 ticks)
        updateTasks.put(player.getUniqueId(), updateTask);
        
        // Initial update
        updateDuelScoreboard(player, roundsDuel);
    }
    
    /**
     * OPTIMIZED: High-performance scoreboard update with minimal overhead
     */
    public void updateDuelScoreboard(Player player, RoundsDuel roundsDuel) {
        FastBoard board = playerBoards.get(player.getUniqueId());
        if (board == null) return;
        
        List<String> lines = scoreboardConfig.getStringList("lines");
        Player opponent = roundsDuel.getOpponent(player);
        
        if (opponent == null) return;
        
        // PERFORMANCE: Pre-allocate list with known size
        List<String> processedLines = new ArrayList<>(lines.size());
        
        // Process each line
        for (String line : lines) {
            String processedLine = replacePlaceholders(line, player, opponent, roundsDuel);
            
            // OPTIMIZED: Efficient color translation
            processedLine = translateColors(processedLine);
            
            processedLines.add(processedLine);
        }
        
        // PERFORMANCE: Single batch update
        board.updateLines(processedLines);
    }
    
    public void removeDuelScoreboard(Player player) {
        // Cancel update task
        BukkitRunnable updateTask = updateTasks.remove(player.getUniqueId());
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        // Remove FastBoard
        FastBoard board = playerBoards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }
    
    private String replacePlaceholders(String line, Player player, Player opponent, RoundsDuel roundsDuel) {
        // OPTIMIZED: Cache duration calculation to reduce repeated operations
        long currentTime = System.currentTimeMillis();
        long durationMs = currentTime - roundsDuel.getStartTime();
        
        UUID playerId = player.getUniqueId();
        Long lastUpdate = lastDurationUpdate.get(playerId);
        String duration = cachedDuration.get(playerId);
        
        // Update duration every second to reduce overhead
        if (lastUpdate == null || currentTime - lastUpdate >= 1000) {
            long durationSeconds = durationMs / 1000;
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            duration = String.format("%02d:%02d", minutes, seconds);
            cachedDuration.put(playerId, duration);
            lastDurationUpdate.put(playerId, currentTime);
        }
        
        // Get scores
        int playerScore = player.equals(roundsDuel.getPlayer1()) ? roundsDuel.getPlayer1Wins() : roundsDuel.getPlayer2Wins();
        int opponentScore = player.equals(roundsDuel.getPlayer1()) ? roundsDuel.getPlayer2Wins() : roundsDuel.getPlayer1Wins();
        
        return line
            .replace("<rounds>", String.valueOf(roundsDuel.getTargetRounds()))
            .replace("<duration>", duration != null ? duration : "00:00")
            .replace("<current_round>", String.valueOf(roundsDuel.getCurrentRound()))
            .replace("<player_score>", String.valueOf(playerScore))
            .replace("<opponent_score>", String.valueOf(opponentScore))
            .replace("<player_name>", truncateName(player.getName(), 8))
            .replace("<opponent_name>", truncateName(opponent.getName(), 8));
    }
    
    private String truncateName(String name, int maxLength) {
        if (name.length() <= maxLength) {
            return name;
        }
        return name.substring(0, maxLength - 1) + "…";
    }
    
    /**
     * OPTIMIZED: High-performance color translation with caching
     */
    private String translateColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // PERFORMANCE: Cache lookup first
        String cached = colorCache.get(text);
        if (cached != null) {
            return cached;
        }
        
        String result = text;
        
        // OPTIMIZED: Efficient color conversion
        result = convertHexColors(result);
        result = convertMiniHexColors(result);
        result = convertGradients(result);
        result = ChatColor.translateAlternateColorCodes('&', result);
        
        // PERFORMANCE: Smart cache management
        if (colorCache.size() < 50) { // Smaller cache for better performance
            colorCache.put(text, result);
        }
        
        return result;
    }
    
    private String convertHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            try {
                String replacement = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
                matcher.appendReplacement(buffer, replacement);
            } catch (Exception e) {
                // Fallback to legacy color if hex fails
                matcher.appendReplacement(buffer, ChatColor.WHITE.toString());
            }
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    private String convertMiniHexColors(String text) {
        Matcher matcher = MINI_HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            try {
                String replacement = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
                matcher.appendReplacement(buffer, replacement);
            } catch (Exception e) {
                // Fallback to legacy color if hex fails
                matcher.appendReplacement(buffer, ChatColor.WHITE.toString());
            }
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    private String convertGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String startColor = matcher.group(1);
            String content = matcher.group(3);
            try {
                String replacement = net.md_5.bungee.api.ChatColor.of(startColor).toString() + content;
                matcher.appendReplacement(buffer, replacement);
            } catch (Exception e) {
                // Fallback to just the content
                matcher.appendReplacement(buffer, content);
            }
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    public void reloadConfig() {
        loadScoreboardConfig();
    }
    
    /**
     * Clean up all scoreboards and tasks
     */
    public void cleanup() {
        // Cancel all update tasks
        for (BukkitRunnable task : updateTasks.values()) {
            task.cancel();
        }
        updateTasks.clear();
        
        // Delete all FastBoards
        for (FastBoard board : playerBoards.values()) {
            board.delete();
        }
        playerBoards.clear();
        
        // Clear caches
        lastDurationUpdate.clear();
        cachedDuration.clear();
    }
}