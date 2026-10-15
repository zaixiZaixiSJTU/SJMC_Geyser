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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.geysermc.geyser.GeyserImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YggdrasilAuthenticationServiceTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/yggdrasil";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void authenticatesAndUsesCustomJoinEndpoint() throws Exception {
        AtomicReference<JsonObject> authenticateRequest = new AtomicReference<>();
        AtomicReference<JsonObject> joinRequest = new AtomicReference<>();
        server.createContext("/api/yggdrasil/authserver/authenticate", exchange -> {
            authenticateRequest.set(readJson(exchange));
            respond(exchange, 200, """
                {"accessToken":"access-token","clientToken":"client-token","selectedProfile":{
                  "id":"12345678123456781234567812345678","name":"TestPlayer"
                }}
                """);
        });
        server.createContext("/api/yggdrasil/sessionserver/session/minecraft/join", exchange -> {
            joinRequest.set(readJson(exchange));
            respond(exchange, 204, "");
        });
        server.createContext("/api/yggdrasil/sessionserver/session/minecraft/profile/12345678123456781234567812345678", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("unsigned=false", exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                {"id":"12345678123456781234567812345678","name":"TestPlayer","properties":[{
                  "name":"textures","value":"texture-value","signature":"texture-signature"
                }]}
                """);
        });

        YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(baseUrl);
        YggdrasilAuthenticationService.AuthenticationResult result = service.authenticate("player-name", "secret");
        service.sessionService().joinServer(result.profile(), result.accessToken(), "server-hash");

        assertEquals("player-name", authenticateRequest.get().get("username").getAsString());
        assertEquals("secret", authenticateRequest.get().get("password").getAsString());
        assertEquals("Minecraft", authenticateRequest.get().getAsJsonObject("agent").get("name").getAsString());
        assertEquals("12345678-1234-5678-1234-567812345678", result.profile().getId().toString());
        assertEquals("TestPlayer", result.profile().getName());
        assertEquals("texture-value", result.profile().getProperty("textures").getValue());
        assertEquals("texture-signature", result.profile().getProperty("textures").getSignature());
        assertEquals("12345678123456781234567812345678", joinRequest.get().get("selectedProfile").getAsString());
        assertEquals("server-hash", joinRequest.get().get("serverId").getAsString());
    }

    @Test
    void exposesYggdrasilErrorMessage() {
        server.createContext("/api/yggdrasil/authserver/authenticate", exchange -> respond(exchange, 403,
            "{\"error\":\"ForbiddenOperationException\",\"errorMessage\":\"Invalid credentials.\"}"));

        YggdrasilAuthenticationException exception = assertThrows(YggdrasilAuthenticationException.class,
            () -> new YggdrasilAuthenticationService(baseUrl).authenticate("player-name", "wrong"));

        assertEquals("Invalid credentials.", exception.getMessage());
    }

    @Test
    void rejectsInsecureRemoteEndpoint() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new YggdrasilAuthenticationService("http://example.com/api/yggdrasil"));
        assertTrue(exception.getMessage().contains("HTTPS"));
    }

    @Test
    void onlyAllowsTexturesFromConfiguredHttpsOrigin() {
        String apiUrl = "https://skin.mc.sjtu.cn/api/yggdrasil/";

        assertTrue(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "https://skin.mc.sjtu.cn/textures/abc", apiUrl));
        assertTrue(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "https://skin.mc.sjtu.cn:443/textures/abc", apiUrl));
        assertFalse(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "http://skin.mc.sjtu.cn/textures/abc", apiUrl));
        assertFalse(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "https://skin.mc.sjtu.cn.evil.example/textures/abc", apiUrl));
        assertFalse(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "https://skin.mc.sjtu.cn:8443/textures/abc", apiUrl));
        assertFalse(YggdrasilAuthenticationService.isTextureUrlAllowed(
            "https://user@skin.mc.sjtu.cn/textures/abc", apiUrl));
    }

    @Test
    void profileLookupFailureDoesNotPreventLogin() throws Exception {
        server.createContext("/api/yggdrasil/authserver/authenticate", exchange -> respond(exchange, 200, """
            {"accessToken":"access-token","clientToken":"client-token","selectedProfile":{
              "id":"12345678123456781234567812345678","name":"TestPlayer"
            }}
            """));
        server.createContext("/api/yggdrasil/sessionserver/session/minecraft/profile/", exchange ->
            respond(exchange, 503, ""));

        YggdrasilAuthenticationService.AuthenticationResult result =
            new YggdrasilAuthenticationService(baseUrl).authenticate("player-name", "secret");

        assertTrue(result.profile().getProperties().isEmpty());
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return GeyserImpl.GSON.fromJson(body, JsonObject.class);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
