package com.example.automation;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.automation.model.Workflow;

/**
 * Fetches workflow listings and content from a JFrog Artifactory generic repository
 * using the Artifactory Storage REST API.
 */
public class ArtifactoryClient {

    @FunctionalInterface
    public interface Fetcher {
        HttpResponse fetch(String url, String authHeader) throws IOException;
    }

    public static final class HttpResponse {
        public final int status;
        public final String body;
        public HttpResponse(int status, String body) { this.status = status; this.body = body; }
    }

    private final String user;
    private final String apiKey;
    private final Fetcher fetcher;
    private final Gson gson = new Gson();

    /** Production constructor — reads credentials from environment variables. */
    public ArtifactoryClient() {
        this(System.getenv("ARTIFACTORY_USER"),
             System.getenv("ARTIFACTORY_API_KEY"),
             ArtifactoryClient::httpFetch);
    }

    /** Test constructor — injects credentials and a fake fetcher. */
    public ArtifactoryClient(String user, String apiKey, Fetcher fetcher) {
        this.user = user == null ? "" : user;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.fetcher = fetcher;
    }

    public List<RemoteWorkflow> listWorkflows() throws ArtifactoryException {
        String auth = basicAuth();
        String listingUrl = ArtifactoryConfig.listingUrl();
        HttpResponse resp = doFetch(listingUrl, auth);
        checkStatus(resp.status);

        JsonObject root = gson.fromJson(resp.body, JsonObject.class);
        JsonArray children = root.getAsJsonArray("children");
        List<RemoteWorkflow> result = new ArrayList<>();
        for (JsonElement el : children) {
            JsonObject child = el.getAsJsonObject();
            if (child.get("folder").getAsBoolean()) continue;
            String uri = child.get("uri").getAsString();
            String filename = uri.startsWith("/") ? uri.substring(1) : uri;
            if (!filename.endsWith(".json")) continue;

            String raw = downloadWorkflow(filename);
            Workflow wf = gson.fromJson(raw, Workflow.class);
            result.add(new RemoteWorkflow(filename, wf, raw));
        }
        return result;
    }

    public String downloadWorkflow(String filename) throws ArtifactoryException {
        String url = ArtifactoryConfig.folderUrl() + "/" + filename;
        HttpResponse resp = doFetch(url, basicAuth());
        checkStatus(resp.status);
        return resp.body;
    }

    private String basicAuth() throws ArtifactoryException {
        if (user.isBlank() || apiKey.isBlank())
            throw new ArtifactoryException(
                "Artifactory credentials not configured (ARTIFACTORY_USER / ARTIFACTORY_API_KEY missing)");
        String token = Base64.getEncoder().encodeToString(
            (user + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private HttpResponse doFetch(String url, String auth) throws ArtifactoryException {
        try {
            return fetcher.fetch(url, auth);
        } catch (IOException e) {
            throw new ArtifactoryException("Could not reach Artifactory: " + e.getMessage(), e);
        }
    }

    private static void checkStatus(int status) throws ArtifactoryException {
        if (status == 401 || status == 403)
            throw new ArtifactoryException("Artifactory authentication failed (HTTP " + status + ")");
        if (status >= 400)
            throw new ArtifactoryException("Artifactory returned HTTP " + status);
    }

    private static HttpResponse httpFetch(String urlStr, String authHeader) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("Authorization", authHeader);
        int status = conn.getResponseCode();
        try (InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpResponse(status, body);
        } finally {
            conn.disconnect();
        }
    }
}
