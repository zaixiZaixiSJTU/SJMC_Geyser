/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.geysermc.geyser.session.auth.yggdrasil;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Minimal client for the authlib-injector compatible Yggdrasil API.
 */
public final class YggdrasilAuthenticationService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final URI baseUri;
    private final HttpClient httpClient;

    public YggdrasilAuthenticationService(String apiUrl) {
        this.baseUri = normalizeBaseUri(apiUrl);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    public AuthenticationResult authenticate(String identifier, String password) throws IOException {
        if (identifier == null || identifier.isBlank() || password == null || password.isEmpty()) {
            throw new YggdrasilAuthenticationException("Account and password are required");
        }

        String clientToken = UUID.randomUUID().toString();
        AuthenticateRequest request = new AuthenticateRequest(
            new Agent("Minecraft", 1), identifier, password, clientToken, false
        );
        AuthenticateResponse response = post("authserver/authenticate", request, AuthenticateResponse.class);
        if (response == null || response.accessToken == null || response.accessToken.isBlank() ||
            response.selectedProfile == null || response.selectedProfile.name == null || response.selectedProfile.name.isBlank()) {
            throw new YggdrasilAuthenticationException("Authentication server returned no selected profile");
        }

        GameProfile profile = new GameProfile(parseUuid(response.selectedProfile.id), response.selectedProfile.name);
        populateProfileProperties(profile);
        return new AuthenticationResult(profile, response.accessToken, response.clientToken);
    }

    public SessionService sessionService() {
        return new CustomSessionService(this);
    }

    public void validate(AuthenticationResult result) throws IOException {
        post("authserver/validate", new ValidateRequest(result.accessToken(), result.clientToken()), Void.class);
        populateProfileProperties(result.profile());
    }

    /**
     * Returns whether a texture URL belongs to the configured Yggdrasil service.
     * Only HTTPS URLs on the exact configured host and port are trusted.
     */
    public static boolean isTextureUrlAllowed(String textureUrl, String apiUrl) {
        try {
            URI texture = URI.create(textureUrl).normalize();
            URI api = normalizeBaseUri(apiUrl);
            return "https".equalsIgnoreCase(texture.getScheme())
                && texture.getUserInfo() == null
                && texture.getHost() != null
                && texture.getHost().equalsIgnoreCase(api.getHost())
                && effectivePort(texture) == effectivePort(api)
                && texture.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void populateProfileProperties(GameProfile profile) {
        try {
            String id = profile.getId().toString().replace("-", "");
            ProfileResponse response = get("sessionserver/session/minecraft/profile/" + id + "?unsigned=false", ProfileResponse.class);
            if (response == null || !profile.getId().equals(parseUuid(response.id)) ||
                response.name == null || !profile.getName().equals(response.name)) {
                return;
            }

            if (response.properties != null) {
                profile.setProperties(response.properties.stream()
                    .filter(property -> property != null && property.name != null && property.value != null)
                    .map(property -> new GameProfile.Property(property.name, property.value, property.signature))
                    .toList());
            }
        } catch (IOException ignored) {
            // A profile texture failure must not prevent an otherwise valid account from joining.
        }
    }

    private void joinServer(GameProfile profile, String accessToken, String serverId) throws IOException {
        String selectedProfile = profile.getId().toString().replace("-", "");
        post("sessionserver/session/minecraft/join", new JoinRequest(accessToken, selectedProfile, serverId), Void.class);
    }

    private <T> T post(String relativePath, Object body, Class<T> responseType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(relativePath))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(GeyserImpl.GSON.toJson(body)))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the authentication server", e);
        } catch (IllegalArgumentException e) {
            throw new YggdrasilAuthenticationException("Invalid Yggdrasil endpoint", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw errorFromResponse(response.statusCode(), response.body());
        }
        if (responseType == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }

        try {
            return GeyserImpl.GSON.fromJson(response.body(), responseType);
        } catch (JsonParseException e) {
            throw new YggdrasilAuthenticationException("Authentication server returned invalid JSON", e);
        }
    }

    private <T> T get(String relativePath, Class<T> responseType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(relativePath))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the authentication server", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw errorFromResponse(response.statusCode(), response.body());
        }
        try {
            return GeyserImpl.GSON.fromJson(response.body(), responseType);
        } catch (JsonParseException e) {
            throw new YggdrasilAuthenticationException("Authentication server returned invalid JSON", e);
        }
    }

    private static YggdrasilAuthenticationException errorFromResponse(int status, String responseBody) {
        String message = null;
        try {
            JsonObject error = GeyserImpl.GSON.fromJson(responseBody, JsonObject.class);
            if (error != null && error.has("errorMessage")) {
                message = error.get("errorMessage").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Fall back to a status-only error without reflecting arbitrary HTML to the player.
        }
        if (message == null || message.isBlank()) {
            message = "Yggdrasil request failed with HTTP " + status;
        }
        if (message.length() > 256) {
            message = message.substring(0, 256);
        }
        return new YggdrasilAuthenticationException(message);
    }

    private static URI normalizeBaseUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Yggdrasil API URL cannot be empty");
        }

        URI uri = URI.create(value.endsWith("/") ? value : value + '/').normalize();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean loopbackHttp = "http".equals(scheme) && uri.getHost() != null &&
            ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost()));
        if (!"https".equals(scheme) && !loopbackHttp) {
            throw new IllegalArgumentException("Yggdrasil API URL must use HTTPS (HTTP is allowed only for loopback testing)");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid Yggdrasil API URL");
        }
        return uri;
    }

    private static UUID parseUuid(String value) throws YggdrasilAuthenticationException {
        if (value == null) {
            throw new YggdrasilAuthenticationException("Authentication server returned an invalid profile UUID");
        }
        String normalized = value.replace("-", "");
        if (normalized.length() != 32) {
            throw new YggdrasilAuthenticationException("Authentication server returned an invalid profile UUID");
        }
        try {
            return UUID.fromString(normalized.substring(0, 8) + '-' + normalized.substring(8, 12) + '-' +
                normalized.substring(12, 16) + '-' + normalized.substring(16, 20) + '-' + normalized.substring(20));
        } catch (IllegalArgumentException e) {
            throw new YggdrasilAuthenticationException("Authentication server returned an invalid profile UUID", e);
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public record AuthenticationResult(GameProfile profile, String accessToken, String clientToken) {
    }

    public record SavedLogin(String profileId, String profileName, String accessToken, String clientToken) {
        public AuthenticationResult result() {
            return new AuthenticationResult(new GameProfile(UUID.fromString(profileId), profileName), accessToken, clientToken);
        }

        public static SavedLogin from(AuthenticationResult result) {
            return new SavedLogin(result.profile().getId().toString(), result.profile().getName(), result.accessToken(), result.clientToken());
        }
    }

    private record Agent(String name, int version) {
    }

    private record AuthenticateRequest(Agent agent, String username, String password, String clientToken,
                                       boolean requestUser) {
    }

    private record ProfileResponse(String id, String name, List<PropertyResponse> properties) {
    }

    private record PropertyResponse(String name, String value, String signature) {
    }

    private record AuthenticateResponse(String accessToken, String clientToken, ProfileResponse selectedProfile) {
    }

    private record JoinRequest(String accessToken, String selectedProfile, String serverId) {
    }

    private record ValidateRequest(String accessToken, String clientToken) {
    }

    private static final class CustomSessionService extends SessionService {
        private final YggdrasilAuthenticationService authenticationService;

        private CustomSessionService(YggdrasilAuthenticationService authenticationService) {
            this.authenticationService = authenticationService;
        }

        @Override
        public void joinServer(GameProfile profile, String authenticationToken, String serverId) throws IOException {
            authenticationService.joinServer(profile, authenticationToken, serverId);
        }
    }
}
