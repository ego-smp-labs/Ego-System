package vn.nirussv.egosystem.update.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vn.nirussv.egosystem.util.HttpClientUtil;

import java.util.concurrent.CompletableFuture;

public class ModrinthSource implements UpdateSource {

    private final HttpClientUtil http;

    public ModrinthSource(HttpClientUtil http) {
        this.http = http;
    }

    @Override
    public boolean canHandle(String url) {
        return url.contains("modrinth.com/plugin") || url.contains("modrinth.com/project");
    }

    @Override
    public CompletableFuture<String> getLatestVersion(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String slug = extractSlug(url);
                // Get version list, filtered by loader could be done here but simple is fine for now
                // We assume the project is a plugin, so default loaders (spigot/paper) usually apply.
                // Better approach: Get versions, filter for 'bukkit'/'spigot'/'paper' loader
                String apiUrl = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders=[\"spigot\",\"paper\",\"bukkit\"]";
                
                JsonElement json = http.getJson(apiUrl);
                JsonArray versions = json.getAsJsonArray();
                
                if (versions.size() > 0) {
                    return versions.get(0).getAsJsonObject().get("version_number").getAsString();
                }
                return null;
            } catch (Exception e) {
                // throw new RuntimeException("Failed to check Modrinth version for " + url, e);
                return null; // Fail silently or return null
            }
        });
    }

    @Override
    public CompletableFuture<String> getDownloadUrl(String url, String version) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String slug = extractSlug(url);
                // We re-fetch versions to get the file url. Efficient usage would cache or pass data, 
                // but this is decoupled.
                String apiUrl = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders=[\"spigot\",\"paper\",\"bukkit\"]";
                JsonElement json = http.getJson(apiUrl);
                JsonArray versions = json.getAsJsonArray();
                
                if (versions.size() > 0) {
                    // Start checking from the most recent version
                    for (JsonElement v : versions) {
                        JsonObject verObj = v.getAsJsonObject();
                        if (verObj.get("version_number").getAsString().equals(version)) {
                             JsonArray files = verObj.getAsJsonArray("files");
                             if (files.size() > 0) {
                                 // Return first file (usually the main jar)
                                 return files.get(0).getAsJsonObject().get("url").getAsString();
                             }
                        }
                    }
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get Modrinth download URL for " + url, e);
            }
        });
    }

    private String extractSlug(String url) {
        // format: active/modrinth.com/plugin/SLUG
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
}
