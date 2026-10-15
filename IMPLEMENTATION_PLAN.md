# SJMC Geyser implementation plan

This repository is based on upstream Geyser and adds a generic Yggdrasil login path.

## Scope

- Add `yggdrasil` as a Java authentication type.
- Read the third-party Yggdrasil base URL from `java.yggdrasil-api-url`.
- Collect credentials through an encrypted Bedrock form without persisting passwords.
- Call the standard Yggdrasil `authenticate` endpoint.
- Use the returned profile, UUID, and access token for the Java protocol session.
- Route the online-mode `join` request to the configured Yggdrasil session server.
- Cover request/response and endpoint behavior with focused tests.

## Verified external contracts

- Geyser upstream: <https://github.com/GeyserMC/Geyser>
- MCProtocolLib custom session service flag: <https://github.com/GeyserMC/MCProtocolLib>
- authlib-injector API specification: <https://github.com/yushijinhun/authlib-injector/wiki>
- SJMC API root: <https://skin.mc.sjtu.cn/api/yggdrasil>

The SJMC API metadata was checked on 2026-09-10 and reports Blessing Skin Yggdrasil API 5.2.2-0.2.6 with `feature.non_email_login` enabled.
