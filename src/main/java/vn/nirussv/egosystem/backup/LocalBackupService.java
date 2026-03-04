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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.util.concurrent.TimeUnit;

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
            String format = config.getBackupFormat();
            if ("tar.gz".equals(format)) {
                backupPath = backupFolder.resolve(backupName + ".tar.gz");
                createTarGzBackup(backupPath, filesToBackup, serverRoot);
            } else if ("zip".equals(format)) {
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
                syncToGitHub(backupPath, backupName);
            }
            if (config.isGoogleDriveEnabled()) {
                syncToGoogleDrive(backupPath);
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
     * Creates a TAR.GZ backup.
     */
    private void createTarGzBackup(Path tarPath, Map<String, Path> files, Path serverRoot) throws IOException {
        try (OutputStream fos = new FileOutputStream(tarPath.toFile());
             GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tao = new TarArchiveOutputStream(gzo)) {
            
            tao.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            for (Map.Entry<String, Path> entry : files.entrySet()) {
                try {
                    File file = entry.getValue().toFile();
                    TarArchiveEntry tarEntry = new TarArchiveEntry(file, entry.getKey());
                    tao.putArchiveEntry(tarEntry);
                    if (file.isFile()) {
                        Files.copy(entry.getValue(), tao);
                    }
                    tao.closeArchiveEntry();
                } catch (IOException e) {
                    if (config.isVerbose()) {
                        plugin.getLogger().warning("Could not backup (tar): " + entry.getKey() + " - " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Syncs the backup to GitHub as a Release Asset.
     */
    private void syncToGitHub(Path backupPath, String releaseName) {
        String token = config.getGitHubToken();
        String repo = config.getGitHubRepository();
        if (token == null || token.isEmpty() || repo == null || repo.isEmpty()) {
            plugin.getLogger().warning("GitHub sync enabled but token/repo is missing.");
            return;
        }

        plugin.getLogger().info("Starting GitHub sync to " + repo + " (Release: " + releaseName + ")");
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build();
            
        Gson gson = new Gson();

        try {
            // 1. Create a release
            JsonObject releaseBody = new JsonObject();
            releaseBody.addProperty("tag_name", releaseName);
            releaseBody.addProperty("name", "Backup " + releaseName);
            releaseBody.addProperty("body", "Automated backup generated on " + new Date().toString());
            releaseBody.addProperty("draft", false);
            releaseBody.addProperty("prerelease", false);

            RequestBody body = RequestBody.create(releaseBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request createReleaseReq = new Request.Builder()
                    .url("https://api.github.com/repos/" + repo + "/releases")
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .post(body)
                    .build();

            String uploadUrl;
            try (Response response = client.newCall(createReleaseReq).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("Failed to create GitHub release: " + response.code() + " " + response.body().string());
                    return;
                }
                JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
                uploadUrl = jsonResponse.get("upload_url").getAsString().replace("{?name,label}", "");
            }

            // 2. Upload the file as a release asset
            File file = backupPath.toFile();
            RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
            Request uploadReq = new Request.Builder()
                    .url(uploadUrl + "?name=" + file.getName())
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .post(fileBody)
                    .build();

            try (Response response = client.newCall(uploadReq).execute()) {
                if (response.isSuccessful()) {
                    plugin.getLogger().info("Successfully uploaded backup to GitHub Releases!");
                } else {
                    plugin.getLogger().warning("Failed to upload backup asset to GitHub: " + response.code() + " " + response.body().string());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Exception during GitHub sync", e);
        }
    }

    /**
     * Syncs the backup to Google Drive.
     */
    private void syncToGoogleDrive(Path backupPath) {
        String folderId = config.getGoogleDriveFolderId();
        String clientId = config.getGoogleDriveClientId();
        String clientSecret = config.getGoogleDriveClientSecret();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            plugin.getLogger().warning("Google Drive: client-id or client-secret not set in config.yml. Run /ssm backup gdrive link.");
            return;
        }

        // Read refresh_token from credential file
        File credFile = new File(plugin.getDataFolder(), "GoogleDriveCredential.json");
        if (!credFile.exists()) {
            plugin.getLogger().warning("Google Drive not linked yet! Run /ssm backup gdrive link in-game.");
            return;
        }

        plugin.getLogger().info("Starting Google Drive sync...");
        Gson gson = new Gson();
        String refreshToken = null;

        try (FileReader reader = new FileReader(credFile)) {
            JsonObject creds = gson.fromJson(reader, JsonObject.class);
            if (creds.has("refresh_token")) refreshToken = creds.get("refresh_token").getAsString();
            else {
                plugin.getLogger().warning("GoogleDriveCredential.json missing 'refresh_token'. Run /ssm backup gdrive link.");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read GoogleDriveCredential.json", e);
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build();

        try {
            // 1. Get Access Token
            RequestBody tokenBody = new FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build();

            Request tokenReq = new Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(tokenBody)
                    .build();

            String token;
            try (Response response = client.newCall(tokenReq).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("Google Drive: Failed to get auth token: " + response.code() + " " + response.body().string());
                    return;
                }
                JsonObject resObj = gson.fromJson(response.body().string(), JsonObject.class);
                token = resObj.get("access_token").getAsString();
            }

            // 2. Upload file
            File file = backupPath.toFile();
            
            JsonObject metadata = new JsonObject();
            metadata.addProperty("name", file.getName());
            if (folderId != null && !folderId.isEmpty()) {
                com.google.gson.JsonArray parents = new com.google.gson.JsonArray();
                parents.add(folderId);
                metadata.add("parents", parents);
            }

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, RequestBody.create(metadata.toString(), MediaType.parse("application/json; charset=UTF-8")))
                    .addFormDataPart("file", file.getName(), RequestBody.create(file, MediaType.parse("application/octet-stream")))
                    .build();

            Request uploadReq = new Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer " + token)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(uploadReq).execute()) {
                if (response.isSuccessful()) {
                    plugin.getLogger().info("Successfully uploaded backup to Google Drive!");
                } else {
                    plugin.getLogger().warning("Google Drive upload failed: " + response.code() + " " + response.body().string());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Exception during Google Drive sync", e);
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
     * Prepares the restore context by fetching the file from the requested source (local, gdrive, github).
     */
    public void prepareRestoreContext(String source, String fileIdentifier, boolean unzip, org.bukkit.command.CommandSender sender) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Path serverRoot = resolveServerRoot();
                Path restorePendingFolder = serverRoot.resolve("plugins/Ego-System/restore_pending");
                Files.createDirectories(restorePendingFolder);
                
                // Clear old pending restores
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(restorePendingFolder)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            deleteDirectory(entry);
                        } else {
                            Files.delete(entry);
                        }
                    }
                }
                
                Path targetFile = restorePendingFolder.resolve(fileIdentifier);
                sender.sendMessage("§e[Restore] Bắt đầu tải dữ liệu từ nguồn: " + source + "...");

                boolean downloadSuccess = false;
                switch (source.toLowerCase()) {
                    case "local" -> {
                        Path localBackup = serverRoot.resolve(config.getBackupFolder()).resolve(fileIdentifier);
                        if (Files.exists(localBackup)) {
                            if (Files.isDirectory(localBackup)) {
                                sender.sendMessage("§e[Restore] Đang copy thư mục backup local...");
                                createFolderBackup(targetFile, collectAllDirectoryFiles(localBackup));
                                downloadSuccess = true;
                            } else {
                                Files.copy(localBackup, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                downloadSuccess = true;
                            }
                        } else {
                            sender.sendMessage("§c[Restore] Không tìm thấy file local này.");
                        }
                    }
                    case "gdrive" -> {
                        downloadSuccess = downloadFromGoogleDrive(fileIdentifier, targetFile, sender);
                    }
                    case "github" -> {
                        downloadSuccess = downloadFromGitHub(fileIdentifier, targetFile, sender);
                    }
                    default -> sender.sendMessage("§c[Restore] Nguồn không hợp lệ, hãy chọn local, gdrive hoặc github.");
                }

                if (!downloadSuccess) {
                    sender.sendMessage("§c[Restore] Tải dữ liệu thất bại.");
                    return;
                }

                sender.sendMessage("§a[Restore] Đã tải thành công file backup. Đang vào thư mục restore_pending.");

                if (unzip && !Files.isDirectory(targetFile)) {
                    sender.sendMessage("§e[Restore] Bắt đầu giải nén archive...");
                    Path extractFolder = restorePendingFolder.resolve("extracted_" + fileIdentifier.replace(".zip", "").replace(".tar.gz", ""));
                    Files.createDirectories(extractFolder);
                    extractArchive(targetFile, extractFolder);
                    sender.sendMessage("§a[Restore] Giải nén thành công.");
                }

                sender.sendMessage("§c§l[Cảnh báo Quan Trọng]");
                sender.sendMessage("§eDữ liệu restore đã sẵn sàng tại plugins/Ego-System/restore_pending/");
                sender.sendMessage("§eServer đang chạy, việc chép đè sẽ làm hỏng world (Windows locks).");
                sender.sendMessage("§aNếu bạn muốn tắt ngay Server để áp dụng file thủ công, hãy gõ: §f/ssm restore confirm-stop");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi chạy prepareRestoreContext", e);
                sender.sendMessage("§c[Restore] Đã xảy ra lỗi tàn bạo, xem console!");
            }
        });
    }

    private Map<String, Path> collectAllDirectoryFiles(Path dir) throws IOException {
        Map<String, Path> map = new HashMap<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                map.put(dir.relativize(file).toString().replace("\\", "/"), file);
                return FileVisitResult.CONTINUE;
            }
        });
        return map;
    }

    private boolean downloadFromGoogleDrive(String fileName, Path dest, org.bukkit.command.CommandSender sender) {
        String folderId = config.getGoogleDriveFolderId();
        String clientId = config.getGoogleDriveClientId();
        String clientSecret = config.getGoogleDriveClientSecret();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            sender.sendMessage("§cGoogle Drive: client-id hoặc client-secret chưa thiết lập trong config.yml.");
            return false;
        }

        File credFile = new File(plugin.getDataFolder(), "GoogleDriveCredential.json");
        if (!credFile.exists()) {
            sender.sendMessage("§cGoogle Drive chưa liên kết! Chạy /ssm backup gdrive link.");
            return false;
        }

        Gson gson = new Gson();
        String refreshToken = null;

        try (FileReader reader = new FileReader(credFile)) {
             JsonObject creds = gson.fromJson(reader, JsonObject.class);
             if (creds.has("refresh_token")) refreshToken = creds.get("refresh_token").getAsString();
             else {
                 sender.sendMessage("§cThiếu refresh_token. Chạy /ssm backup gdrive link để liên kết lại.");
                 return false;
             }
        } catch (Exception e) {
             sender.sendMessage("§cLỗi đọc file GoogleDriveCredential.json");
             return false;
        }

        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build();

        try {
            // Get token
            RequestBody tokenBody = new FormBody.Builder()
                    .add("client_id", clientId).add("client_secret", clientSecret)
                    .add("refresh_token", refreshToken).add("grant_type", "refresh_token").build();

            Request tokenReq = new Request.Builder().url("https://oauth2.googleapis.com/token").post(tokenBody).build();

            String token;
            try (Response response = client.newCall(tokenReq).execute()) {
                if (!response.isSuccessful()) return false;
                token = gson.fromJson(response.body().string(), JsonObject.class).get("access_token").getAsString();
            }

            // Search file in folder
            HttpUrl searchUrl = HttpUrl.parse("https://www.googleapis.com/drive/v3/files").newBuilder()
                    .addQueryParameter("q", "name='" + fileName + "' and '" + folderId + "' in parents and trashed=false")
                    .addQueryParameter("fields", "files(id, name)").build();

            Request searchReq = new Request.Builder().url(searchUrl).header("Authorization", "Bearer " + token).get().build();
            String fileId = null;

            try (Response response = client.newCall(searchReq).execute()) {
                if (!response.isSuccessful()) return false;
                JsonObject resObj = gson.fromJson(response.body().string(), JsonObject.class);
                var jsonArray = resObj.getAsJsonArray("files");
                if (jsonArray.size() > 0) fileId = jsonArray.get(0).getAsJsonObject().get("id").getAsString();
            }

            if (fileId == null) {
                sender.sendMessage("§cKhông tìm thấy file trên Google Drive thư mục này.");
                return false;
            }

            // Download
            Request downloadReq = new Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media")
                    .header("Authorization", "Bearer " + token).get().build();

            try (Response response = client.newCall(downloadReq).execute()) {
                if (!response.isSuccessful()) return false;
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(dest.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                         fos.write(buffer, 0, bytesRead);
                    }
                }
            }
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Gdrive download fail", e);
            return false;
        }
    }

    private boolean downloadFromGitHub(String releaseName, Path dest, org.bukkit.command.CommandSender sender) {
        String token = config.getGitHubToken();
        String repo = config.getGitHubRepository();
        if (token == null || token.isEmpty() || repo == null || repo.isEmpty()) {
            sender.sendMessage("§cGitHub sync chưa thiết lập token/repo.");
            return false;
        }

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build();
            
        Gson gson = new Gson();

        try {
            // Get release by tag
            Request req = new Request.Builder()
                    .url("https://api.github.com/repos/" + repo + "/releases/tags/" + releaseName)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json").get().build();

            String assetUrl = null;
            try (Response response = client.newCall(req).execute()) {
                if (!response.isSuccessful()) {
                     sender.sendMessage("§cKhông tìm thấy release " + releaseName);
                     return false;
                }
                JsonObject resObj = gson.fromJson(response.body().string(), JsonObject.class);
                var assets = resObj.getAsJsonArray("assets");
                if (assets.size() > 0) {
                     assetUrl = assets.get(0).getAsJsonObject().get("url").getAsString();
                }
            }

            if (assetUrl == null) {
                sender.sendMessage("§Release không có file asset đính kèm.");
                return false;
            }

            // Download asset
            Request dlReq = new Request.Builder()
                    .url(assetUrl)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/octet-stream").get().build();

            try (Response response = client.newCall(dlReq).execute()) {
                if (!response.isSuccessful()) return false;
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(dest.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                         fos.write(buffer, 0, bytesRead);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Github download fail", e);
            return false;
        }
    }

    private void extractArchive(Path archive, Path destDir) throws IOException {
        String fileName = archive.getFileName().toString();
        if (fileName.endsWith(".tar.gz")) {
             try (InputStream fi = Files.newInputStream(archive);
                  BufferedInputStream bi = new BufferedInputStream(fi);
                  GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
                  TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {
                  org.apache.commons.compress.archivers.ArchiveEntry entry;
                  while ((entry = ti.getNextEntry()) != null) {
                       extractEntry(destDir, entry.getName(), entry.isDirectory(), ti);
                  }
             }
        } else if (fileName.endsWith(".zip")) {
             try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
                  java.util.zip.ZipEntry ze = zis.getNextEntry();
                  while (ze != null) {
                       extractEntry(destDir, ze.getName(), ze.isDirectory(), zis);
                       ze = zis.getNextEntry();
                  }
                  zis.closeEntry();
             }
        }
    }

    private void extractEntry(Path destDir, String name, boolean isDirectory, InputStream is) throws IOException {
        Path newPath = destDir.resolve(name);
        // Prevent path traversal
        if (!newPath.normalize().startsWith(destDir.normalize())) return;

        if (isDirectory) {
            Files.createDirectories(newPath);
        } else {
            Files.createDirectories(newPath.getParent());
            Files.copy(is, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
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

    // ==================== Google Drive OAuth Link Flow ====================

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

    /**
     * Checks whether Google Drive is already linked (refresh_token exists).
     */
    public boolean isGoogleDriveLinked() {
        File credFile = new File(plugin.getDataFolder(), "GoogleDriveCredential.json");
        if (!credFile.exists()) return false;
        try (FileReader reader = new FileReader(credFile)) {
            JsonObject creds = new Gson().fromJson(reader, JsonObject.class);
            return creds.has("refresh_token") && !creds.get("refresh_token").getAsString().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates the Google OAuth authorization URL.
     * The user must open this URL in a browser, authorize, and paste the returned code.
     *
     * @return the authorization URL, or null if client-id is not configured
     */
    public String generateGoogleDriveAuthUrl() {
        String clientId = config.getGoogleDriveClientId();
        if (clientId.isEmpty()) return null;

        return GOOGLE_AUTH_URL
                + "?client_id=" + clientId
                + "&redirect_uri=" + REDIRECT_URI
                + "&response_type=code"
                + "&scope=" + DRIVE_SCOPE
                + "&access_type=offline"
                + "&prompt=consent";
    }

    /**
     * Exchanges the authorization code for tokens and saves refresh_token to GoogleDriveCredential.json.
     *
     * @param authCode the authorization code from the user
     * @return true if successful
     */
    public boolean exchangeGoogleDriveCode(String authCode) {
        String clientId = config.getGoogleDriveClientId();
        String clientSecret = config.getGoogleDriveClientSecret();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            plugin.getLogger().warning("Google Drive: client-id or client-secret not set in config.yml.");
            return false;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody body = new FormBody.Builder()
                .add("code", authCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
                .build();

        Request request = new Request.Builder()
                .url(GOOGLE_TOKEN_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                plugin.getLogger().warning("Google OAuth token exchange failed: " + response.code() + " " + response.body().string());
                return false;
            }

            Gson gson = new Gson();
            JsonObject tokenResponse = gson.fromJson(response.body().string(), JsonObject.class);

            if (!tokenResponse.has("refresh_token")) {
                plugin.getLogger().warning("Google OAuth response missing refresh_token. Try revoking access and re-linking.");
                return false;
            }

            // Save refresh_token to file
            JsonObject credData = new JsonObject();
            credData.addProperty("refresh_token", tokenResponse.get("refresh_token").getAsString());
            if (tokenResponse.has("access_token")) {
                credData.addProperty("access_token", tokenResponse.get("access_token").getAsString());
            }

            File credFile = new File(plugin.getDataFolder(), "GoogleDriveCredential.json");
            Files.createDirectories(credFile.getParentFile().toPath());
            try (FileWriter writer = new FileWriter(credFile)) {
                gson.toJson(credData, writer);
            }

            plugin.getLogger().info("Google Drive linked successfully! refresh_token saved.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Google OAuth token exchange error", e);
            return false;
        }
    }
}
