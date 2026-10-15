# TODO

## 正确显示第三方 Yggdrasil 皮肤

- [x] 从 SJMC Profile 查询响应中读取 `properties` / `textures`；
- [x] 解析 Base64 编码的 textures 属性，提取皮肤 URL、披风 URL 和模型类型；
- [x] 仅允许配置的 Yggdrasil HTTPS 同源材质进入下载流程，并禁止跨站重定向；
- [x] 在 Java 登录会话和 Bedrock 玩家实体生成时使用对应皮肤，而不是空皮肤或默认皮肤；
- [x] 增加带签名 textures 属性、URL 边界和 Profile 查询失败回退测试；
- [x] 使用真实 SJMC 账号进行一次人工端到端验证（2026-10-15：基岩账号 crazymax1145 以 zyw0o0 成功登录并进入服务器）。

## Minecraft 26.3 支持

- [x] 将 SJMC Yggdrasil 改动移植到上游 master（已合并 26.3 支持，PR #6712），分支 `sjmc-26.3`；
- [x] 构建 Geyser-Spigot 2.12.0-SNAPSHOT，并在 Purpur 26.3 测试服成功启用（配置迁移至 config-version 10，yggdrasil 设置保留）；
- [x] 在 26.3 测试服使用真实 SJMC 账号完成端到端验证（2026-10-15，登录与显示正常）。
