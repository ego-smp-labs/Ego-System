package vn.nirussv.egosystem.update.source;

import java.util.concurrent.CompletableFuture;

public interface UpdateSource {
    /**
     * Checks if this source can handle the given URL.
     */
    boolean canHandle(String url);

    /**
     * Gets the latest version info for the plugin.
     * @return CompletableFuture containing the version string, or exception.
     */
    CompletableFuture<String> getLatestVersion(String url);

    /**
     * Gets the download URL for the latest version.
     */
    CompletableFuture<String> getDownloadUrl(String url, String version);
}
