# Third-party Yggdrasil authentication

This fork adds an authlib-injector compatible login mode to Geyser. It is configured for the SJMC user center by default, but the implementation is not tied to SJMC.

## Configuration

Generate or update Geyser's `config.yml`, then configure the Java section:

```yaml
java:
  address: 127.0.0.1
  port: 25565
  auth-type: yggdrasil
  yggdrasil-api-url: https://skin.mc.sjtu.cn/api/yggdrasil
```

The Java server must use the same API root in authlib-injector:

```text
-javaagent:/path/to/authlib-injector.jar=https://skin.mc.sjtu.cn/api/yggdrasil
```

Only HTTPS endpoints are accepted. Plain HTTP is accepted for loopback addresses so local integration tests and development servers can be used safely.

## Login flow

After the Bedrock resource-pack phase, Geyser opens a login form. The account field accepts any identifier supported by the configured service. SJMC advertises `feature.non_email_login`, so a profile name can be used and is the recommended choice when an account owns multiple profiles.

The password is sent over the already encrypted Bedrock connection and then over HTTPS to `POST /authserver/authenticate`. It is retained only for the duration of that request and is never written to disk. The returned access token, client token, selected profile, and UUID are persisted by Bedrock XUID in `saved-yggdrasil-logins.json`; reconnects first call `POST /authserver/validate` and only show the login form if validation fails. During the Java encryption handshake, `POST /sessionserver/session/minecraft/join` is sent to the same configured API root.

After authentication, Geyser requests the selected profile with `unsigned=false` and preserves its texture property. Alternative Yggdrasil signatures are not checked against Mojang's unrelated public key; instead, texture downloads are restricted to HTTPS URLs on the exact host and port configured by `yggdrasil-api-url`. Redirects are not followed. If profile or image retrieval fails, login continues with Geyser's normal fallback skin.

Bedrock custom forms do not provide a masked-password input type, so the password is visible while it is being entered. Use the form away from screen capture or shoulder-surfing risks.

If the authentication server does not return `selectedProfile`, login is rejected. For multi-profile accounts, sign in with the desired profile name when the service supports non-email login.

## Build

JDK 21 is required by the current upstream Geyser build:

```powershell
.\gradlew.bat build
```

Standalone artifacts are produced under `bootstrap/standalone/build/libs`.

## Security notes

- Do not put credentials in `config.yml`, command arguments, or logs.
- Keep Bedrock login validation enabled so an attacker cannot impersonate a player's Bedrock identity.
- Restrict access to the Geyser configuration and logs; authentication-server errors may be logged for diagnostics.
- Saved Yggdrasil tokens are credentials: restrict access to `saved-yggdrasil-logins.json` and remove it to force all players to sign in again.
