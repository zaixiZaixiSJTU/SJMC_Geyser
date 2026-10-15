# SJMC Geyser

基于 [Geyser](https://github.com/GeyserMC/Geyser) 的分支，为使用 **authlib-injector** 的 Java 服务器增加第三方 Yggdrasil 认证：基岩版玩家可以使用 SJMC（或任意兼容 authlib-injector 的）账号登录 Java 服务器。

- 支持 Spigot / Paper / Purpur 插件与 Fabric 服务器模组（Minecraft 26.3）
- 认证服务默认指向 SJMC 用户中心，也可配置为任意兼容 authlib-injector 的 Yggdrasil API
- 支持第三方皮肤显示；密码不落盘，登录令牌按基岩版 XUID 保存，令牌有效时重连免密

预编译 jar 在 [`builds/`](builds/) 目录。

## 安装与配置

### 1. 放置文件

| 平台 | 放入目录 | 额外要求 |
| --- | --- | --- |
| Spigot / Paper / Purpur | `plugins/` | 无 |
| Fabric 服务器 | `mods/` | 需要 fabric-api（对应 26.3 的版本） |

### 2. Java 服务端启用 authlib-injector

在服务端启动参数中加入（地址必须与 Geyser 配置一致）：

```text
-javaagent:authlib-injector.jar=https://skin.mc.sjtu.cn/api/yggdrasil/
```

### 3. 配置 Geyser

编辑配置文件（位置见下一节），关键项：

```yaml
java:
  auth-type: yggdrasil
  yggdrasil-api-url: https://skin.mc.sjtu.cn/api/yggdrasil/
```

- `auth-type: yggdrasil` 启用第三方认证；原有的 `online`、`offline`、`floodgate` 模式不受影响
- `yggdrasil-api-url` 仅接受 HTTPS（`localhost`、`127.0.0.1` 等回环地址允许 HTTP，便于本地测试）

### 4. 玩家登录

基岩版客户端连接后会弹出「第三方 Java 账号」表单，输入账号与密码即可进入。若该基岩账号的令牌仍然有效，则直接进入；认证失败时错误信息会显示在表单中。

## 配置与数据存储位置

| 平台 | 配置文件 | 登录令牌文件 |
| --- | --- | --- |
| Spigot / Paper / Purpur | `plugins/Geyser-Spigot/config.yml` | `plugins/Geyser-Spigot/saved-yggdrasil-logins.json` |
| Fabric | `config/Geyser-Fabric/config.yml` | `config/Geyser-Fabric/saved-yggdrasil-logins.json` |

- 密码不会保存到任何位置
- `saved-yggdrasil-logins.json` 按基岩版 XUID 保存访问令牌与 Profile 信息，删除该文件可强制所有玩家重新登录
- 该文件包含可登录凭据，请限制文件访问权限

## 构建

需要 JDK 21（构建 Fabric 模组时 Gradle 会使用 JDK 25 工具链）：

```bash
# Spigot 插件
./gradlew :spigot:build
# 产物：bootstrap/spigot/build/libs/Geyser-Spigot.jar

# Fabric 模组
./gradlew :fabric:build :fabric:mergeShadowAndJarJar
# 产物：bootstrap/mod/fabric/build/libs/Geyser-Fabric.jar
```

## 更多文档

- [第三方 Yggdrasil 认证：流程与安全说明](docs/yggdrasil-authentication.md)
- [上游 Geyser 安装与使用文档](https://geysermc.org/wiki/geyser/)

本项目基于 Geyser，遵循其 MIT 许可证。
