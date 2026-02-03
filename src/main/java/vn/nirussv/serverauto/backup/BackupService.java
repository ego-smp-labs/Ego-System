package vn.nirussv.serverauto.backup;

import vn.nirussv.serverauto.ServerAutoPlugin;
import vn.nirussv.serverauto.config.ConfigManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for performing server backups to GitHub.
 */
public class BackupService {

    private final ServerAutoPlugin plugin;
    private final ConfigManager config;
    private final GitHubClient gitHubClient;
    
    private long lastBackupTime = 0;
    private String lastBackupStatus = "Never";

    public BackupService(ServerAutoPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.gitHubClient = new GitHubClient(plugin, config);
    }

    /**
     * Performs a backup of configured paths.
     * 
     * @param synchronous If true, runs synchronously
     * @return true if successful
     */
    public boolean performBackup(boolean synchronous) {
        if (!config.isGitHubEnabled()) {
            plugin.getLogger().warning("GitHub backup is disabled in config!");
            return false;
        }
        
        if (config.getGitHubToken().isEmpty()) {
            plugin.getLogger().warning("GitHub token not configured!");
            return false;
        }
        
        Runnable backupTask = () -> {
            try {
                plugin.getLogger().info("Starting backup...");
                long startTime = System.currentTimeMillis();
                
                List<String> paths = config.getBackupPaths();
                Map<String, byte[]> filesToUpload = collectFiles(paths);
                
                if (filesToUpload.isEmpty()) {
                    plugin.getLogger().warning("No files to backup!");
                    lastBackupStatus = "No files found";
                    return;
                }
                
                String commitMessage = formatCommitMessage();
                int uploaded = 0;
                int failed = 0;
                
                if (config.isCompressionEnabled()) {
                    // Upload as a single zip file
                    byte[] zipData = createZip(filesToUpload);
                    String zipName = "backup-" + new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date()) + ".zip";
                    
                    if (gitHubClient.uploadFile("backups/" + zipName, zipData, commitMessage)) {
                        uploaded = filesToUpload.size();
                        plugin.getLogger().info("Uploaded backup archive: " + zipName + " (" + filesToUpload.size() + " files)");
                    } else {
                        failed = filesToUpload.size();
                    }
                } else {
                    // Upload individual files
                    for (Map.Entry<String, byte[]> entry : filesToUpload.entrySet()) {
                        if (gitHubClient.uploadFile(entry.getKey(), entry.getValue(), commitMessage)) {
                            uploaded++;
                        } else {
                            failed++;
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                lastBackupTime = System.currentTimeMillis();
                lastBackupStatus = String.format("Success: %d files, %d failed (%.1fs)", 
                        uploaded, failed, duration / 1000.0);
                
                plugin.getLogger().info("Backup completed! " + lastBackupStatus);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Backup failed!", e);
                lastBackupStatus = "Failed: " + e.getMessage();
            }
        };
        
        if (synchronous) {
            backupTask.run();
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, backupTask);
        }
        
        return true;
    }

    /**
     * Collects files matching the configured paths.
     */
    private Map<String, byte[]> collectFiles(List<String> patterns) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Path serverRoot = plugin.getServer().getWorldContainer().toPath().getParent();
        
        for (String pattern : patterns) {
            collectMatchingFiles(serverRoot, pattern, files);
        }
        
        return files;
    }

    /**
     * Collects files matching a glob pattern.
     */
    private void collectMatchingFiles(Path root, String pattern, Map<String, byte[]> files) throws IOException {
        // Handle directory patterns (ending with /)
        if (pattern.endsWith("/")) {
            Path dir = root.resolve(pattern.substring(0, pattern.length() - 1));
            if (Files.isDirectory(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (shouldIncludeFile(file)) {
                            String relativePath = root.relativize(file).toString().replace("\\", "/");
                            files.put(relativePath, Files.readAllBytes(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return;
        }
        
        // Handle glob patterns
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        Path baseDir = root;
        
        // Extract base directory from pattern
        int slashIndex = pattern.indexOf('/');
        if (slashIndex > 0 && !pattern.substring(0, slashIndex).contains("*")) {
            baseDir = root.resolve(pattern.substring(0, slashIndex));
            pattern = pattern.substring(slashIndex + 1);
        }
        
        if (!Files.exists(baseDir)) return;
        
        final String finalPattern = pattern;
        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (matchesSimpleGlob(fileName, finalPattern) && shouldIncludeFile(file)) {
                    String relativePath = root.relativize(file).toString().replace("\\", "/");
                    files.put(relativePath, Files.readAllBytes(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Simple glob matching for file names.
     */
    private boolean matchesSimpleGlob(String name, String pattern) {
        if (pattern.equals("*")) return true;
        if (pattern.startsWith("*.")) {
            return name.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }

    /**
     * Checks if a file should be included in backup.
     */
    private boolean shouldIncludeFile(Path file) {
        String name = file.getFileName().toString();
        // Exclude large binary files and session locks
        if (name.endsWith(".jar") || name.equals("session.lock")) {
            return false;
        }
        // Exclude very large files (> 50MB)
        try {
            if (Files.size(file) > 50 * 1024 * 1024) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Creates a zip archive from files.
     */
    private byte[] createZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Formats the commit message with placeholders.
     */
    private String formatCommitMessage() {
        String template = config.getCommitMessageTemplate();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        
        return template
                .replace("%date%", dateFormat.format(now))
                .replace("%time%", timeFormat.format(now))
                .replace("%server%", plugin.getServer().getName());
    }

    public long getLastBackupTime() {
        return lastBackupTime;
    }

    public String getLastBackupStatus() {
        return lastBackupStatus;
    }
}
