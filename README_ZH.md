<img src="https://geysermc.org/img/geyser-1760-860.png" alt="Geyser" width="600"/>

[English](README.md) | 简体中文

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Discord](https://img.shields.io/discord/613163671870242838.svg?color=%237289da&label=discord)](https://discord.gg/geysermc)

# SJMC Geyser

本项目基于 [Geyser](https://github.com/GeyserMC/Geyser)，为使用 authlib-injector 的 Java 服务器增加可配置的第三方 Yggdrasil 登录能力。默认认证服务为 SJMC 用户中心，但实现本身不与 SJMC 硬编码绑定。

项目解决的核心问题是：让基岩版玩家通过合法取得的第三方 Java Profile、UUID 和 Session 身份连接启用了第三方正版验证的 Java 服务器，而不是绕过服务器认证或使用离线身份。

## 当前功能

- 新增 `yggdrasil` Java 认证类型；
- 支持配置任意兼容 authlib-injector 规范的 Yggdrasil API；
- 通过基岩版表单输入第三方账号和密码；
- 调用标准 `/authserver/authenticate` 接口；
- 使用返回的 `accessToken`、`selectedProfile` 和 UUID 创建 Java 登录会话；
- 将 Java 在线模式握手中的 `/sessionserver/session/minecraft/join` 请求发送到同一个第三方认证服务；
- 支持用户名等非邮箱凭据，具体取决于认证服务能力；
- 保持 Geyser 原有 `online`、`offline` 和 `floodgate` 模式不变；
- 登录失败时向玩家显示认证服务返回的错误信息。
- 按基岩版 XUID 持久化登录令牌；令牌有效时重连无需再次输入密码。
- 重连前调用 `/authserver/validate` 校验令牌，失效后自动清除并重新要求登录。
- 持久化文件只保存令牌、客户端令牌和 Profile 信息，不保存 SJMC 密码。
- 从 Yggdrasil Profile 响应读取皮肤、披风和模型类型，并转换为基岩版玩家外观。
- 允许下载配置认证服务器同一 HTTPS 主机上的材质，同时拒绝相似域名、HTTP 地址和跨站重定向。

SJMC API 当前公开声明 `feature.non_email_login: true`。当账号拥有多个角色时，建议直接使用目标角色名登录，以便认证服务返回明确的 `selectedProfile`。

## 暂未实现

以下属于后续功能，不应视为当前版本已经支持：

- access token 的刷新（当前令牌失效后需要重新输入凭据）；
- 多角色选择界面；

当前版本将令牌保存在 Geyser 数据目录的 `saved-yggdrasil-logins.json` 中，不保存密码。令牌失效或 XUID 变化时需要重新登录。如果认证响应没有 `selectedProfile`，登录会被拒绝。该文件包含可用于登录的敏感令牌，应限制文件权限；删除该文件可强制所有玩家重新登录。

## 环境要求

- JDK 21；
- 一个可用的 Geyser 部署环境；
- Java 服务端使用 authlib-injector，并且与 Geyser 配置相同的 Yggdrasil API；
- 基岩版监听端口允许 UDP 流量；
- Java 服务端版本应符合当前 Geyser 上游的支持范围。

Geyser 的平台部署方法仍可参考[上游安装文档](https://geysermc.org/wiki/geyser/setup/)。

## 配置

在 Geyser 的 `config.yml` 中设置 Java 服务器和认证方式：

```yaml
java:
  address: 127.0.0.1
  port: 25565
  auth-type: yggdrasil
  yggdrasil-api-url: https://skin.mc.sjtu.cn/api/yggdrasil
```

Java 服务端必须使用相同的 API 地址启动 authlib-injector，例如：

```text
-javaagent:/path/to/authlib-injector.jar=https://skin.mc.sjtu.cn/api/yggdrasil
```

远程认证地址必须使用 HTTPS。为便于本地开发和集成测试，回环地址 `localhost`、`127.0.0.1` 和 `::1` 可以使用 HTTP。

## 登录流程

```text
Minecraft Bedrock 客户端
          │
          ▼
       Geyser
          │ 显示第三方账号登录表单
          ▼
Yggdrasil /authserver/authenticate
          │ 返回 accessToken 与 selectedProfile
          ▼
       Java 登录握手
          │
          ▼
Yggdrasil /sessionserver/session/minecraft/join
          │
          ▼
启用 authlib-injector 的 Java 服务端
```

密码通过已加密的 Bedrock 连接传给 Geyser，再通过 HTTPS 发送给 Yggdrasil 服务。密码只在本次认证请求期间使用，不会写入配置文件或令牌文件。

## 安全说明

- 基岩版自定义表单没有密码掩码输入类型，输入时密码会显示在屏幕上；
- 不要在直播、录屏或可能被旁观的环境中输入密码；
- 不要把账号密码写入 `config.yml`、命令行参数或日志；
- 保持 Geyser 的基岩版登录验证开启，防止攻击者伪造玩家身份；
- 只使用可信的 HTTPS Yggdrasil 服务；
- 限制对 Geyser 配置和日志的访问权限；认证失败原因可能会写入日志用于诊断。

更详细的英文部署与安全说明见 [docs/yggdrasil-authentication.md](docs/yggdrasil-authentication.md)。

## 构建

## 一键启动本地测试服

双击项目根目录的 `start.bat`，即可启动测试服目录中的 Paper + Geyser。Paper 控制台会在新窗口打开；已安装并运行 playit 时会复用现有 Agent，否则会尝试启动测试服目录内的 `playit.exe`。

基岩版玩家请使用 playit 控制台为 Bedrock/UDP 隧道分配的公网域名和端口。不要使用 Java TCP 隧道，也不要将 `25565` 暴露给公网。

克隆仓库后初始化子模块：

```powershell
git submodule update --init --recursive
```

运行测试并构建全部模块：

```powershell
.\gradlew.bat build
```

只构建 Standalone 可运行包：

```powershell
.\gradlew.bat :standalone:shadowJar
```

输出文件位于：

```text
bootstrap/standalone/build/libs/Geyser-Standalone.jar
```

Linux 或 macOS 使用 `./gradlew` 替代 `.\gradlew.bat`。

## 测试状态

当前实现已通过：

- Geyser Core 完整单元测试；
- 配置生成、默认值及旧配置迁移测试；
- Yggdrasil `authenticate` 请求与响应测试；
- 第三方 Session Server `join` 请求测试；
- 无连字符 Profile UUID 格式测试；
- 认证错误解析及非安全远程 HTTP 地址拒绝测试；
- Standalone 可运行 JAR 构建。

由于仓库中不包含真实 SJMC 测试账号和目标 Java 服务器，真实账号的端到端进服仍需在部署环境中进行人工验证。

## 上游项目

Geyser 是 Minecraft 基岩版与 Java 版之间的协议转换代理。本分支只扩展认证和 Session 建立阶段，没有重写其协议转换核心。

- Geyser 官网：<https://geysermc.org>
- Geyser 文档：<https://geysermc.org/wiki/geyser/>
- Geyser 源码：<https://github.com/GeyserMC/Geyser>
- authlib-injector：<https://github.com/yushijinhun/authlib-injector>
- SJMC 用户中心：<https://skin.mc.sjtu.cn>

## 许可证

本项目沿用 Geyser 的 [MIT License](LICENSE)。提交上游改动前，请同时遵循 Geyser 的贡献规范与 AI 使用政策。
