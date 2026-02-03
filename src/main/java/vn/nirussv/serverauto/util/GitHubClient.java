package vn.nirussv.serverauto.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import vn.nirussv.serverauto.ServerAutoPlugin;
import vn.nirussv.serverauto.config.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * GitHub API client for backup operations.
 * Uses GitHub REST API v3.
 */
public class GitHubClient {

    private static final String API_BASE = "https://api.github.com";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final ServerAutoPlugin plugin;
    private final ConfigManager config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public GitHubClient(ServerAutoPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Creates or updates a file in the repository.
     * 
     * @param path File path in repo
     * @param content File content (will be base64 encoded)
     * @param message Commit message
     * @return true if successful
     */
    public boolean uploadFile(String path, byte[] content, String message) {
        String token = config.getGitHubToken();
        String repo = config.getGitHubRepository();
        String branch = config.getGitHubBranch();
        
        if (token.isEmpty() || repo.isEmpty()) {
            plugin.getLogger().warning("GitHub token or repository not configured!");
            return false;
        }
        
        try {
            // First, try to get existing file SHA (needed for update)
            String sha = getFileSha(path);
            
            // Build request body
            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            body.addProperty("content", Base64.getEncoder().encodeToString(content));
            body.addProperty("branch", branch);
            
            if (sha != null) {
                body.addProperty("sha", sha);
            }
            
            String url = API_BASE + "/repos/" + repo + "/contents/" + path;
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .put(RequestBody.create(body.toString(), JSON))
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    if (config.isVerbose()) {
                        plugin.getLogger().info("Uploaded: " + path);
                    }
                    return true;
                } else {
                    String error = response.body() != null ? response.body().string() : "Unknown error";
                    plugin.getLogger().warning("Failed to upload " + path + ": " + response.code() + " - " + error);
                    return false;
                }
            }
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to upload " + path, e);
            return false;
        }
    }

    /**
     * Gets the SHA of an existing file (needed for updates).
     */
    private String getFileSha(String path) {
        String token = config.getGitHubToken();
        String repo = config.getGitHubRepository();
        String branch = config.getGitHubBranch();
        
        String url = API_BASE + "/repos/" + repo + "/contents/" + path + "?ref=" + branch;
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                return json.has("sha") ? json.get("sha").getAsString() : null;
            }
        } catch (IOException e) {
            // File doesn't exist, that's fine
        }
        
        return null;
    }

    /**
     * Gets the latest release version of a repository.
     *
     * @param repo Repository in owner/repo format
     * @return Version string or null if not found
     */
    public String getLatestReleaseVersion(String repo) {
        String url = API_BASE + "/repos/" + repo + "/releases/latest";
        
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                return json.has("tag_name") ? json.get("tag_name").getAsString() : null;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get latest release for " + repo, e);
        }
        
        return null;
    }

    /**
     * Gets the download URL for the latest release asset.
     *
     * @param repo Repository in owner/repo format
     * @param assetPattern Pattern to match asset name (e.g., "*.jar")
     * @return Download URL or null
     */
    public String getLatestReleaseAssetUrl(String repo, String assetPattern) {
        String url = API_BASE + "/repos/" + repo + "/releases/latest";
        
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                if (json.has("assets")) {
                    for (var element : json.getAsJsonArray("assets")) {
                        JsonObject asset = element.getAsJsonObject();
                        String name = asset.get("name").getAsString();
                        if (matchesPattern(name, assetPattern)) {
                            return asset.get("browser_download_url").getAsString();
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get release assets for " + repo, e);
        }
        
        return null;
    }

    /**
     * Downloads a file from URL.
     *
     * @param url Download URL
     * @return File content or null
     */
    public byte[] downloadFile(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to download " + url, e);
        }
        
        return null;
    }

    /**
     * Simple glob pattern matching.
     */
    private boolean matchesPattern(String name, String pattern) {
        if (pattern.startsWith("*")) {
            return name.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }
}
