# SJMC Geyser 第三方 Yggdrasil 认证适配项目

## 1. 项目启动背景

### 1.1 当前场景

SJMC 使用基于 Blessing Skin 的第三方 Yggdrasil 认证服务：

```text
https://skin.mc.sjtu.cn/api/yggdrasil
```

该服务承担 Java Edition 玩家身份认证、角色 Profile、UUID、Session 以及皮肤纹理等相关能力。

与此同时，Geyser 的核心定位是 Minecraft Bedrock Edition 与 Java Edition 之间的协议转换层，使基岩版客户端能够连接 Java 版服务器。

### 1.2 当前问题

现有 Geyser 的常规使用场景主要围绕以下身份模式：

- Java 正版 / Microsoft 账号认证；
- Java 服务端离线模式；
- Floodgate 提供的 Bedrock 身份接入。

SJMC 的服务器环境则使用第三方 Yggdrasil 认证体系。因此，即使 Geyser 已经能够完成 Bedrock Protocol 与 Java Protocol 之间的大部分协议转换，也仍然缺少一条关键链路：

> **Bedrock 玩家身份 → SJMC 第三方 Yggdrasil Java 身份 → Java Session**

也就是说，目前主要障碍并不是 Minecraft 数据包本身的翻译，而是 **Geyser 与第三方 Java 身份认证体系之间的衔接**。

### 1.3 对 `online-mode` 的理解

原生 Java 服务端中：

```properties
online-mode=true
```

通常意味着服务器要求经过 Mojang / Microsoft 官方身份验证。

但在部署 authlib-injector 等第三方 Yggdrasil 认证方案后，服务器仍可以保持在线身份验证流程，只是认证中心由 Mojang 官方服务替换为指定的第三方 Yggdrasil 服务。

因此，本项目的目标并不是简单绕过 `online-mode`，也不是把玩家作为离线账号强行送入服务器，而是尝试让 Geyser **正确取得并使用 SJMC Yggdrasil 所认可的 Java 身份**。

---

## 2. 项目核心思路

将整个连接过程抽象为：

```text
Minecraft Bedrock Client
          │
          │ Bedrock Protocol
          ▼
       Geyser
          │
          ├── 协议翻译
          │
          └── 第三方身份认证适配（本项目重点）
                    │
                    ▼
          SJMC Yggdrasil API
                    │
                    ├── accessToken / session
                    ├── selectedProfile
                    ├── UUID
                    └── textures / skin
                    │
                    ▼
             Java Login Session
                    │
                    ▼
             SJMC Java Server
```

Geyser 已经负责复杂的 Bedrock ↔ Java 协议转换，因此项目初期不计划重写协议翻译系统。

主要工作集中在 **Java 登录、认证、Profile 与 Session 建立阶段**。

---

## 3. 初步项目目标

### 3.1 第一阶段：可行性验证

首先确认当前 Geyser 的 Java 登录调用链，包括：

```text
Bedrock 玩家建立连接
        ↓
Geyser Session
        ↓
选择 Java Auth Mode
        ↓
取得 Java 身份
        ↓
建立 Java Protocol Session
        ↓
登录后端 Java Server
```

重点确认：

1. Geyser 当前 `ONLINE / OFFLINE / FLOODGATE` 等认证模式的实现位置；
2. Java Profile、UUID 与 Session Token 在何处生成或注入；
3. Geyser 使用的 Java 协议库是否允许注入第三方 Yggdrasil Session；
4. 第三方 Yggdrasil 是否会与现有 Java 登录流程产生额外兼容问题。

第一阶段最终目标是回答：

> **在不大规模重写 Geyser 的情况下，能否增加 SJMC Yggdrasil 身份认证。**

### 3.2 第二阶段：实现通用 Yggdrasil Auth Provider

如果第一阶段验证可行，不将功能硬编码成 `SJMCAuth`，而优先设计通用第三方认证层，例如：

```text
AuthProvider
├── Microsoft / Official
├── Offline
├── Floodgate
└── Custom Yggdrasil
```

第三方 Yggdrasil Provider 通过配置指定认证服务：

```yaml
auth-type: yggdrasil

yggdrasil:
  api-url: "https://skin.mc.sjtu.cn/api/yggdrasil"
```

这样 SJMC 只是该 Provider 的一个实际部署实例，而不是代码中唯一支持的认证站点。

### 3.3 第三阶段：完成 SJMC 登录流程

目标是实现：

```text
Bedrock 玩家
    ↓
提供 / 绑定 SJMC 身份
    ↓
SJMC Yggdrasil Authenticate
    ↓
取得 accessToken
    ↓
取得 selectedProfile
    ↓
建立 Java Session
    ↓
进入 SJMC Java Server
```

SJMC Yggdrasil 当前声明：

```json
"feature.non_email_login": true
```

因此登录层应考虑同时兼容用户名等非邮箱登录标识，而不能假定登录账号一定是邮箱地址。

---

## 4. 初步功能范围

项目初期只解决“能否正确登录并进入服务器”这一核心问题。

### P0：必须实现

- Geyser 能连接使用 SJMC Yggdrasil 认证的 Java 服务端；
- 能取得有效的第三方 Java Profile；
- UUID 与 Java 角色身份正确；
- Java Session 验证能够通过；
- Bedrock 玩家能够正常进入服务器；
- 不破坏 Geyser 原有的协议转换核心功能。

### P1：后续完善

- Bedrock XUID ↔ SJMC Java 账号绑定；
- Token 安全存储与刷新；
- 避免每次进入服务器重新输入账号密码；
- 第三方皮肤与纹理 Profile 兼容；
- 登录失败原因反馈；
- 多 Yggdrasil Endpoint 配置支持。

### 暂不作为第一阶段目标

- 重写 Geyser 的 Bedrock ↔ Java 协议翻译；
- 自行设计新的 Minecraft 身份认证协议；
- 绕过服务器身份认证；
- 伪造 Mojang / Microsoft 正版身份；
- 大规模修改 Floodgate；
- 在可行性尚未确认前设计复杂的账号管理系统。

---

## 5. 项目设计原则

### 5.1 认证适配，而非认证绕过

本项目应始终保持：

```text
Bedrock Identity
       ↓
合法取得 SJMC Java Identity
       ↓
通过 SJMC Yggdrasil Session 验证
```

而不是：

```text
Bedrock Identity
       ↓
伪造 / 绕过 Java 身份验证
```

### 5.2 尽可能保持 Geyser 上游兼容

优先采用新增 Provider、接口或独立模块的方式实现功能，减少对 Geyser 核心 Translator 的侵入式修改。

目标是让未来 Geyser 上游升级时，第三方认证补丁仍然能够较容易地迁移。

### 5.3 SJMC 作为首个实现，Yggdrasil 作为抽象目标

短期目标：

> **让 SJMC Bedrock 玩家能够通过 Geyser 使用 SJMC Java 身份登录。**

长期代码结构目标：

> **让 Geyser 具备可配置的第三方 Yggdrasil Authentication Provider。**

---

## 6. 当前阶段需要研究的问题

下一步应优先调查：

1. 当前最新版 Geyser 的认证相关源码结构；
2. `AuthType` 到 Java Session 创建之间的完整调用链；
3. Geyser 当前 Java Protocol Library 对 Session/Profile 的抽象；
4. Microsoft Auth 是否与 Java Session 建立逻辑强耦合；
5. SJMC Yggdrasil 的 `authenticate / refresh / validate / join / hasJoined` 实际行为；
6. 服务端 authlib-injector 与 Geyser 作为“Java 客户端”时的交互过程；
7. 第三方 textures property 与 Bedrock Skin 转换之间是否存在冲突；
8. 如何在不保存用户明文密码的前提下实现长期账号绑定。

---

## 7. 初步里程碑

```text
M0  项目启动
 │
 ├─ 明确 SJMC 为第三方 Yggdrasil 环境
 └─ 明确问题集中于 Authentication / Session

M1  Geyser 源码认证链分析
 │
 └─ 找到最小修改入口

M2  Yggdrasil 登录 PoC
 │
 └─ 成功取得 SJMC Java Profile + Token

M3  Geyser Session PoC
 │
 └─ Bedrock → Geyser → SJMC Java Server 成功登录

M4  Auth Provider 抽象
 │
 └─ 从 SJMC 专用代码整理为通用 Yggdrasil Provider

M5  账号绑定与 Token 管理
 │
 └─ 达到可供实际玩家使用的状态

M6  皮肤、异常处理及长期维护
```

---

## 8. 项目初步定义

> **本项目计划基于 Geyser 的 Bedrock ↔ Java 协议转换能力，为其增加第三方 Yggdrasil 身份认证适配，使 Bedrock Edition 玩家能够通过合法的 SJMC Java Profile 与 Session 身份连接 SJMC Java 服务器。项目初期以 SJMC 为验证环境，并尽可能将实现抽象为通用、可配置、低侵入的 Yggdrasil Authentication Provider。**

