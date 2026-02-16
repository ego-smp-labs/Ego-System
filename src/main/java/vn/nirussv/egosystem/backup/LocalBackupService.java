package vn.nirussv.egosystem.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.config.ConfigManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for performing LOCAL server backups.
 * Backs up worlds, plugins, and config files to a local folder.
 */
public class LocalBackupService {

    private final EgoSystemPlugin plugin;
    private final ConfigManager config;
    
    private long lastBackupTime = 0;
    private String lastBackupStatus = "Never";
    private String lastBackupFile = null;

    public LocalBackupService(EgoSystemPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Performs a local backup of the server.
     * 
     * @param synchronous If true, runs synchronously (blocking)
     * @return true if backup started successfully
     */
    public boolean performBackup(boolean synchronous) {
        if (!config.isLocalBackupEnabled()) {
            plugin.getLogger().warning("Local backup is disabled!");
            return false;
        }
        
        Runnable task = this::executeBackup;
        
        if (synchronous) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
        
        return true;
    }

    /**
     * Resolves the server root directory from the plugin data folder.
     * Uses getAbsoluteFile() to prevent NPE when getDataFolder() returns a relative path.
     *
     * @return the server root path (parent of the "plugins" directory)
     */
    private Path resolveServerRoot() {
        File dataFolder = plugin.getDataFolder().getAbsoluteFile();
        File pluginsDir = dataFolder.getParentFile();
        if (pluginsDir == null) {
            plugin.getLogger().warning("Could not resolve plugins directory from: " + dataFolder);
            return dataFolder.toPath();
        }
        File serverDir = pluginsDir.getParentFile();
        if (serverDir == null) {
            plugin.getLogger().warning("Could not resolve server root from: " + pluginsDir);
            return pluginsDir.toPath();
        }
        return serverDir.toPath();
    }

    /**
     * Executes the backup process.
     */
    private void executeBackup() {
        try {
            plugin.getLogger().info("Starting local backup...");
            long startTime = System.currentTimeMillis();
            
            // Create backup folder
            Path serverRoot = resolveServerRoot();
            Path backupFolder = serverRoot.resolve(config.getBackupFolder());
            Files.createDirectories(backupFolder);
            
            // Generate backup name
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupName = "backup_" + timestamp;
            
            // Collect files to backup
            Map<String, Path> filesToBackup = new LinkedHashMap<>();
            
            // Backup worlds
            if (config.isBackupWorlds()) {
                collectWorlds(serverRoot, filesToBackup);
            }
            
            // Backup plugins folder
            if (config.isBackupPlugins()) {
                collectPlugins(serverRoot, filesToBackup);
            }
            
            // Backup config files
            if (config.isBackupConfigs()) {
                collectConfigs(serverRoot, filesToBackup);
            }
            
            // Backup additional included paths
            for (String path : config.getIncludePaths()) {
                Path file = serverRoot.resolve(path);
                if (Files.exists(file)) {
                    filesToBackup.put(path, file);
                }
            }
            
            // Remove excluded paths
            List<String> excludes = config.getExcludePaths();
            filesToBackup.entrySet().removeIf(entry -> 
                excludes.stream().anyMatch(pattern -> matchesGlob(entry.getKey(), pattern))
            );
            
            if (filesToBackup.isEmpty()) {
                plugin.getLogger().warning("No files to backup!");
                lastBackupStatus = "No files found";
                return;
            }
            
            // Create backup
            Path backupPath;
            if (config.isCompressionEnabled()) {
                backupPath = backupFolder.resolve(backupName + ".zip");
                createZipBackup(backupPath, filesToBackup, serverRoot);
            } else {
                backupPath = backupFolder.resolve(backupName);
                createFolderBackup(backupPath, filesToBackup);
            }
            
            // Cleanup old backups
            cleanupOldBackups(backupFolder);
            
            long duration = System.currentTimeMillis() - startTime;
            long sizeKB = Files.size(backupPath) / 1024;
            
            lastBackupTime = System.currentTimeMillis();
            lastBackupFile = backupPath.getFileName().toString();
            lastBackupStatus = String.format("Success: %d files, %d KB (%.1fs)", 
                    filesToBackup.size(), sizeKB, duration / 1000.0);
            
            plugin.getLogger().info("Backup completed: " + lastBackupFile);
            plugin.getLogger().info("Size: " + sizeKB + " KB, Files: " + filesToBackup.size());
            
            // Sync to GitHub if enabled
            if (config.isGitHubEnabled() && config.isSyncBackups()) {
                syncToGitHub(backupPath);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Backup failed!", e);
            lastBackupStatus = "Failed: " + e.getMessage();
        }
    }

    /**
     * Collects world folders to backup.
     */
    private void collectWorlds(Path serverRoot, Map<String, Path> files) throws IOException {
        for (World world : Bukkit.getWorlds()) {
            Path worldFolder = world.getWorldFolder().toPath();
            if (Files.exists(worldFolder)) {
                // Save world before backup
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    world.save();
                });
                
                // Wait a bit for save to complete
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                
                collectDirectory(worldFolder, serverRoot, files);
            }
        }
    }

    /**
     * Collects plugin JAR files and their data folders.
     */
    private void collectPlugins(Path serverRoot, Map<String, Path> files) throws IOException {
        Path pluginsFolder = serverRoot.resolve("plugins");
        if (!Files.exists(pluginsFolder)) return;
        
        Files.walkFileTree(pluginsFolder, EnumSet.noneOf(FileVisitOption.class), 2, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String relativePath = serverRoot.relativize(file).toString().replace("\\", "/");
                // Include JARs and config files in plugin folders
                if (file.toString().endsWith(".jar") || 
                    file.toString().endsWith(".yml") ||
                    file.toString().endsWith(".yaml") ||
                    file.toString().endsWith(".json") ||
                    file.toString().endsWith(".properties")) {
                    files.put(relativePath, file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip certain directories
                String name = dir.getFileName().toString();
                if (name.equals("cache") || name.equals("logs") || name.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Collects server config files.
     */
    private void collectConfigs(Path serverRoot, Map<String, Path> files) throws IOException {
        // Root config files
        String[] configFiles = {"server.properties", "bukkit.yml", "spigot.yml", 
                                "paper.yml", "paper-global.yml", "permissions.yml",
                                "commands.yml", "help.yml", "ops.json", "whitelist.json"};
        
        for (String configFile : configFiles) {
            Path file = serverRoot.resolve(configFile);
            if (Files.exists(file)) {
                files.put(configFile, file);
            }
        }
        
        // Config folder if exists
        Path configFolder = serverRoot.resolve("config");
        if (Files.exists(configFolder) && Files.isDirectory(configFolder)) {
            collectDirectory(configFolder, serverRoot, files);
        }
    }

    /**
     * Recursively collects files from a directory.
     */
    private void collectDirectory(Path dir, Path serverRoot, Map<String, Path> files) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Skip session.lock files (Minecraft locks these)
                if (!file.getFileName().toString().equals("session.lock")) {
                    String relativePath = serverRoot.relativize(file).toString().replace("\\", "/");
                    files.put(relativePath, file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a ZIP backup.
     */
    private void createZipBackup(Path zipPath, Map<String, Path> files, Path serverRoot) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (Map.Entry<String, Path> entry : files.entrySet()) {
                try {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zos.putNextEntry(zipEntry);
                    Files.copy(entry.getValue(), zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    if (config.isVerbose()) {
                        plugin.getLogger().warning("Could not backup: " + entry.getKey() + " - " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Creates a folder backup (no compression).
     */
    private void createFolderBackup(Path backupPath, Map<String, Path> files) throws IOException {
        Files.createDirectories(backupPath);
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            Path target = backupPath.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.copy(entry.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Removes old backups exceeding the limit.
     */
    private void cleanupOldBackups(Path backupFolder) throws IOException {
        int maxBackups = config.getMaxBackups();
        if (maxBackups <= 0) return;
        
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupFolder, "backup_*")) {
            for (Path path : stream) {
                backups.add(path);
            }
        }
        
        // Sort by name (timestamp in name = chronological order)
        backups.sort(Comparator.comparing(p -> p.getFileName().toString()));
        
        // Delete oldest backups
        while (backups.size() > maxBackups) {
            Path oldest = backups.remove(0);
            if (Files.isDirectory(oldest)) {
                deleteDirectory(oldest);
            } else {
                Files.delete(oldest);
            }
            plugin.getLogger().info("Deleted old backup: " + oldest.getFileName());
        }
    }

    /**
     * Recursively deletes a directory.
     */
    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Syncs the backup to GitHub.
     */
    private void syncToGitHub(Path backupPath) {
        // TODO: Implement GitHub sync if needed
        if (config.isVerbose()) {
            plugin.getLogger().info("GitHub sync not yet implemented for local backups");
        }
    }

    /**
     * Simple glob pattern matching.
     */
    private boolean matchesGlob(String path, String pattern) {
        if (pattern.startsWith("*")) {
            return path.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 1));
        } else if (pattern.endsWith("/")) {
            return path.startsWith(pattern);
        }
        return path.equals(pattern);
    }

    /**
     * Lists all available backups.
     */
    public List<String> listBackups() {
        List<String> backups = new ArrayList<>();
        Path serverRoot = resolveServerRoot();
        Path backupFolder = serverRoot.resolve(config.getBackupFolder());
        
        if (!Files.exists(backupFolder)) return backups;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupFolder, "backup_*")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                long size = Files.isDirectory(path) ? 
                        Files.walk(path).mapToLong(p -> {
                            try { return Files.size(p); } catch (IOException e) { return 0; }
                        }).sum() : Files.size(path);
                backups.add(name + " (" + (size / 1024) + " KB)");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not list backups: " + e.getMessage());
        }
        
        backups.sort(Collections.reverseOrder());
        return backups;
    }

    /**
     * Restores a backup.
     */
    public boolean restoreBackup(String backupName) {
        // TODO: Implement restore functionality
        plugin.getLogger().warning("Restore functionality not yet implemented");
        return false;
    }

    public long getLastBackupTime() {
        return lastBackupTime;
    }

    public String getLastBackupStatus() {
        return lastBackupStatus;
    }

    public String getLastBackupFile() {
        return lastBackupFile;
    }
}
