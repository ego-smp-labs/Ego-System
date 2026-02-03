package vn.nirussv.serverauto.update;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import vn.nirussv.serverauto.ServerAutoPlugin;
import vn.nirussv.serverauto.backup.GitHubClient;
import vn.nirussv.serverauto.config.ConfigManager;
import vn.nirussv.serverauto.config.ConfigManager.PluginUpdateConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Service for auto-updating plugins.
 */
public class UpdateService {

    private final ServerAutoPlugin plugin;
    private final ConfigManager config;
    private final GitHubClient gitHubClient;
    
    private final Map<String, UpdateInfo> pendingUpdates = new HashMap<>();
    private long lastCheckTime = 0;

    public UpdateService(ServerAutoPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.gitHubClient = new GitHubClient(plugin, config);
    }

    /**
     * Checks for updates for all configured plugins.
     */
    public void checkForUpdates() {
        if (!config.isAutoUpdateEnabled()) {
            return;
        }
        
        plugin.getLogger().info("Checking for plugin updates...");
        lastCheckTime = System.currentTimeMillis();
        
        Map<String, PluginUpdateConfig> pluginsToCheck = config.getPluginsToUpdate();
        
        for (Map.Entry<String, PluginUpdateConfig> entry : pluginsToCheck.entrySet()) {
            String pluginName = entry.getKey();
            PluginUpdateConfig updateConfig = entry.getValue();
            
            checkPluginUpdate(pluginName, updateConfig);
        }
        
        // Notify admins if enabled
        if (config.isNotifyAdmins() && !pendingUpdates.isEmpty()) {
            notifyAdmins();
        }
    }

    /**
     * Checks for update of a specific plugin.
     */
    private void checkPluginUpdate(String pluginName, PluginUpdateConfig updateConfig) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            if (config.isVerbose()) {
                plugin.getLogger().warning("Plugin not found: " + pluginName);
            }
            return;
        }
        
        String currentVersion = targetPlugin.getDescription().getVersion();
        String latestVersion = null;
        String downloadUrl = null;
        
        if ("github".equalsIgnoreCase(updateConfig.source())) {
            latestVersion = gitHubClient.getLatestReleaseVersion(updateConfig.repo());
            if (latestVersion != null) {
                downloadUrl = gitHubClient.getLatestReleaseAssetUrl(updateConfig.repo(), "*.jar");
            }
        } else if ("url".equalsIgnoreCase(updateConfig.source())) {
            // Direct URL doesn't have version checking
            downloadUrl = updateConfig.url();
            latestVersion = "latest";
        }
        
        if (latestVersion == null) {
            if (config.isVerbose()) {
                plugin.getLogger().warning("Could not get latest version for: " + pluginName);
            }
            return;
        }
        
        // Compare versions (simple string comparison, strips 'v' prefix)
        String cleanCurrent = currentVersion.replaceFirst("^v", "");
        String cleanLatest = latestVersion.replaceFirst("^v", "");
        
        if (!cleanCurrent.equals(cleanLatest) && isNewerVersion(cleanLatest, cleanCurrent)) {
            plugin.getLogger().info("Update available for " + pluginName + ": " + currentVersion + " -> " + latestVersion);
            pendingUpdates.put(pluginName, new UpdateInfo(pluginName, currentVersion, latestVersion, downloadUrl, updateConfig.autoApply()));
            
            // Auto-apply if enabled
            if (updateConfig.autoApply()) {
                downloadUpdate(pluginName);
            }
        } else if (config.isVerbose()) {
            plugin.getLogger().info(pluginName + " is up to date: " + currentVersion);
        }
    }

    /**
     * Simple version comparison.
     */
    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            
            for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
                int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i].replaceAll("[^0-9]", "")) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("[^0-9]", "")) : 0;
                
                if (latestNum > currentNum) return true;
                if (latestNum < currentNum) return false;
            }
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            return latest.compareTo(current) > 0;
        }
        return false;
    }

    /**
     * Downloads and stages an update for a plugin.
     */
    public boolean downloadUpdate(String pluginName) {
        UpdateInfo info = pendingUpdates.get(pluginName);
        if (info == null) {
            plugin.getLogger().warning("No pending update for: " + pluginName);
            return false;
        }
        
        if (info.downloadUrl() == null) {
            plugin.getLogger().warning("No download URL for: " + pluginName);
            return false;
        }
        
        plugin.getLogger().info("Downloading update for " + pluginName + "...");
        
        byte[] data = gitHubClient.downloadFile(info.downloadUrl());
        if (data == null) {
            plugin.getLogger().warning("Failed to download update for: " + pluginName);
            return false;
        }
        
        // Save to update folder
        File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }
        
        // Find original plugin file
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) return false;
        
        String fileName = new File(targetPlugin.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
        File updateFile = new File(updateFolder, fileName);
        
        try (FileOutputStream fos = new FileOutputStream(updateFile)) {
            fos.write(data);
            plugin.getLogger().info("Update downloaded: " + updateFile.getName());
            plugin.getLogger().info("Update will be applied on next server restart.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save update for: " + pluginName, e);
            return false;
        }
    }

    /**
     * Applies all pending updates (copies from update folder to plugins folder).
     */
    public void applyUpdates() {
        File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
        if (!updateFolder.exists() || !updateFolder.isDirectory()) {
            plugin.getLogger().info("No pending updates to apply.");
            return;
        }
        
        File[] updates = updateFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (updates == null || updates.length == 0) {
            plugin.getLogger().info("No pending updates to apply.");
            return;
        }
        
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        
        for (File update : updates) {
            File target = new File(pluginsFolder, update.getName());
            try {
                // Backup old version
                if (target.exists()) {
                    File backup = new File(pluginsFolder, update.getName() + ".backup");
                    Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Copy new version
                Files.copy(update.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                // Delete update file
                update.delete();
                
                plugin.getLogger().info("Applied update: " + update.getName());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply update: " + update.getName(), e);
            }
        }
        
        plugin.getLogger().info("Updates applied. Server restart required for changes to take effect.");
    }

    /**
     * Notifies online admins about pending updates.
     */
    private void notifyAdmins() {
        String message = "§e[ServerAuto] §aUpdates available: §f" + String.join(", ", pendingUpdates.keySet());
        
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("serverauto.update"))
                .forEach(p -> p.sendMessage(message));
    }

    public Map<String, UpdateInfo> getPendingUpdates() {
        return Collections.unmodifiableMap(pendingUpdates);
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Information about an available update.
     */
    public record UpdateInfo(String pluginName, String currentVersion, String latestVersion, String downloadUrl, boolean autoApply) {}
}
