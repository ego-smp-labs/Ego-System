package vn.nirussv.egosystem.update.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vn.nirussv.egosystem.util.HttpClientUtil;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubSource implements UpdateSource {

    private final HttpClientUtil http;
    private final Pattern REPO_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/]+)");

    public GitHubSource(HttpClientUtil http) {
        this.http = http;
    }

    @Override
    public boolean canHandle(String url) {
        return url.contains("github.com") && !url.endsWith(".jar");
    }

    @Override
    public CompletableFuture<String> getLatestVersion(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String repo = extractRepo(url);
                String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
                JsonElement json = http.getJson(apiUrl);
                return json.getAsJsonObject().get("tag_name").getAsString();
            } catch (Exception e) {
                throw new RuntimeException("Failed to check GitHub version for " + url, e);
            }
        });
    }

    @Override
    public CompletableFuture<String> getDownloadUrl(String url, String version) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String repo = extractRepo(url);
                String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
                JsonElement json = http.getJson(apiUrl);
                
                // Find first JAR asset
                for (JsonElement asset : json.getAsJsonObject().getAsJsonArray("assets")) {
                    JsonObject assetObj = asset.getAsJsonObject();
                    if (assetObj.get("name").getAsString().endsWith(".jar")) {
                        return assetObj.get("browser_download_url").getAsString();
                    }
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get GitHub download URL for " + url, e);
            }
        });
    }

    private String extractRepo(String url) {
        Matcher matcher = REPO_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1) + "/" + matcher.group(2).replace(".git", "");
        }
        throw new IllegalArgumentException("Invalid GitHub URL: " + url);
    }
}
