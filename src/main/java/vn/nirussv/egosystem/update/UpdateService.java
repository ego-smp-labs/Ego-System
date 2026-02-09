package vn.nirussv.egosystem.update;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.config.ConfigManager;
import vn.nirussv.egosystem.util.HttpClientUtil;
import vn.nirussv.egosystem.update.source.UpdateSource;
import vn.nirussv.egosystem.update.source.GitHubSource;
import vn.nirussv.egosystem.update.source.ModrinthSource;

import java.io.*;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service for auto-updating plugins.
 * Supports GitHub, Modrinth, and direct URLs.
 */
public class UpdateService {

    private final EgoSystemPlugin plugin;
    private final ConfigManager config;
    private final HttpClientUtil httpClient;
    
    private final List<UpdateSource> sources = new ArrayList<>();
    private final Map<String, UpdateInfo> pendingUpdates = new HashMap<>();
    private long lastCheckTime = 0;

    public UpdateService(EgoSystemPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = new HttpClientUtil(config);
        
        // Register sources
        this.sources.add(new GitHubSource(httpClient));
        this.sources.add(new ModrinthSource(httpClient));
    }

    /**
     * Checks for updates for all plugins in list.yml.
     */
    public void checkForUpdates() {
        plugin.getLogger().info("Checking for plugin updates...");
        lastCheckTime = System.currentTimeMillis();
        
        // Load list.yml
        Map<String, String> pluginMap = loadPluginList();
        
        for (Map.Entry<String, String> entry : pluginMap.entrySet()) {
            String name = entry.getKey();
            String url = entry.getValue();
            
            // Skip if this plugin is not installed on the server
            if (Bukkit.getPluginManager().getPlugin(name) == null) {
                continue;
            }
            
            checkSinglePlugin(name, url);
        }
    }
    
    private Map<String, String> loadPluginList() {
        Map<String, String> map = new HashMap<>();
        try (InputStream stream = plugin.getResource("list.yml")) {
            if (stream == null) return map;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
            for (String key : yaml.getKeys(false)) {
                if (yaml.isString(key)) {
                    map.put(key, yaml.getString(key));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load list.yml: " + e.getMessage());
        }
        return map;
    }

    private void checkSinglePlugin(String name, String url) {
        UpdateSource source = sources.stream()
                .filter(s -> s.canHandle(url))
                .findFirst()
                .orElse(null);
                
        if (source == null) {
            // Check if it is a direct JAR url?
            // For now, only GitHub and Modrinth supported for strict version checking
            return; 
        }
        
        source.getLatestVersion(url).thenAccept(latestVersion -> {
            if (latestVersion == null) return;
            
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            String currentVersion = p.getDescription().getVersion();
            
            if (isNewer(latestVersion, currentVersion)) {
                plugin.getLogger().info("Update available for " + name + ": " + currentVersion + " -> " + latestVersion);
                
                source.getDownloadUrl(url, latestVersion).thenAccept(downloadUrl -> {
                    if (downloadUrl != null) {
                        pendingUpdates.put(name, new UpdateInfo(name, currentVersion, latestVersion, downloadUrl, true));
                        if (config.isAutoApply()) {
                            downloadUpdate(name);
                        }
                    }
                });
            }
        }).exceptionally(e -> {
            plugin.getLogger().warning("Error checking update for " + name + ": " + e.getMessage());
            return null;
        });
    }

    private boolean isNewer(String latest, String current) {
        String cleanLatest = latest.replaceAll("[^0-9.]", "");
        String cleanCurrent = current.replaceAll("[^0-9.]", "");
        return stripV(latest).compareToIgnoreCase(stripV(current)) != 0 && !cleanCurrent.equals(cleanLatest); 
        // Simple string check + inequality. Real version comparison is complex (semver etc).
        // For now, if string differs, assume update (naive but safer than missing updates).
        // Improved:
        // return !stripV(latest).equals(stripV(current)); 
    }
    
    private String stripV(String s) {
        return s.toLowerCase().startsWith("v") ? s.substring(1) : s;
    }

    public boolean downloadUpdate(String pluginName) {
        UpdateInfo info = pendingUpdates.get(pluginName);
        if (info == null) return false;
        
        plugin.getLogger().info("Downloading update for " + pluginName + "...");
        
        CompletableFuture.runAsync(() -> {
             try {
                 byte[] data = httpClient.download(info.downloadUrl());
                 File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
                 if (!updateFolder.exists()) updateFolder.mkdirs();
                 
                 // Try to keep original filename if possible, otherwise use PluginName.jar
                 String fileName = pluginName + ".jar";
                 // Try to resolve from URL
                 String urlFile = info.downloadUrl().substring(info.downloadUrl().lastIndexOf('/') + 1);
                 if (urlFile.endsWith(".jar")) fileName = urlFile;
                 
                 File target = new File(updateFolder, fileName);
                 try (FileOutputStream fos = new FileOutputStream(target)) {
                     fos.write(data);
                 }
                 
                 plugin.getLogger().info("Downloaded " + fileName + ". Will apply on restart.");
             } catch (Exception e) {
                 plugin.getLogger().warning("Failed to download " + pluginName + ": " + e.getMessage());
             }
        });
        
        return true;
    }

    public void applyUpdates() {
        // Updates are applied by server on restart if in 'update' folder.
        // We can force move them too if 'useUpdateFolder' is false, but Spigot standard is 'update' folder.
        plugin.getLogger().info("Updates apply on restart automatically.");
    }

    public Map<String, UpdateInfo> getPendingUpdates() {
        return pendingUpdates;
    }
    
    public long getLastCheckTime() {
        return lastCheckTime;
    }
    
    public record UpdateInfo(String pluginName, String currentVersion, String latestVersion, String downloadUrl, boolean autoApply) {}
}
