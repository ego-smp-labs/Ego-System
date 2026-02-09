package vn.nirussv.egosystem.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import vn.nirussv.egosystem.config.ConfigManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpClientUtil {

    private final OkHttpClient client;
    private final ConfigManager config;
    private final Gson gson;

    public HttpClientUtil(ConfigManager config) {
        this.config = config;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getMaxParallelDownloads() > 0 ? 10 : 10, TimeUnit.SECONDS) // Basic timeout
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String get(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        requestBuilder.header("User-Agent", "Ego-System/1.0");
        
        if (url.contains("github.com") && !config.getGitHubKey().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + config.getGitHubKey());
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            if (response.body() == null) throw new IOException("Empty body");
            return response.body().string();
        }
    }

    public byte[] download(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        requestBuilder.header("User-Agent", "Ego-System/1.0");
        
        if (url.contains("github.com") && !config.getGitHubKey().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + config.getGitHubKey());
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            if (response.body() == null) throw new IOException("Empty body");
            return response.body().bytes();
        }
    }
    
    public JsonElement getJson(String url) throws IOException {
        String json = get(url);
        return gson.fromJson(json, JsonElement.class);
    }
}
