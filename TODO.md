# TODO

## 正确显示第三方 Yggdrasil 皮肤

- [x] 从 SJMC Profile 查询响应中读取 `properties` / `textures`；
- [x] 解析 Base64 编码的 textures 属性，提取皮肤 URL、披风 URL 和模型类型；
- [x] 仅允许配置的 Yggdrasil HTTPS 同源材质进入下载流程，并禁止跨站重定向；
- [x] 在 Java 登录会话和 Bedrock 玩家实体生成时使用对应皮肤，而不是空皮肤或默认皮肤；
- [x] 增加带签名 textures 属性、URL 边界和 Profile 查询失败回退测试；
- 使用真实 SJMC 账号进行一次人工端到端验证。
