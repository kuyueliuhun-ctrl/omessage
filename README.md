# OpenCode 通知（Android）

通过局域网连接电脑上的 `opencode serve` 服务器，在以下事件发生时于手机弹窗通知（支持后台/锁屏）：

| 事件 | 说明 | 是否可交互 |
|---|---|---|
| 权限请求 (`permission.asked` / `permission.updated`) | opencode 需要批准执行某操作 | 可「允许一次 / 总是允许 / 拒绝」 |
| 问题抛出 (`question.asked`) | AI 执行中向用户提问 | 可全屏作答 / 跳过 |
| 执行完成 (`session.idle`) | 任务完成 | 仅提示 |
| 执行失败 (`session.error`) | 任务出错 | 仅提示 |

## 1. 电脑端启动 opencode 服务器

```powershell
# 监听局域网（0.0.0.0 表示允许其它设备连接）
opencode serve --hostname 0.0.0.0 --port 4096

# 建议设置密码（App 支持 Basic 认证，用户名默认 opencode）
$env:OPENCODE_SERVER_PASSWORD="你的密码"
opencode serve --hostname 0.0.0.0 --port 4096

# 可选：启用 mDNS 发现
opencode serve --hostname 0.0.0.0 --port 4096 --mdns
```

> - 电脑与手机必须在**同一局域网**。
> - 需在 Windows 防火墙放行 4096 端口（或按提示允许）。
> - 电脑运行 `opencode`（TUI）时本身也会启动一个服务器，但默认只监听 `127.0.0.1`；请用上面的 `--hostname 0.0.0.0` 显式启动。

## 2. 推荐：让问题直接抛出（避免被提问权限拦截）

在项目的 `opencode.json` 中加入，让 AI 提问无需额外批准，手机才能直接收到问题并作答：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "permission": {
    "question": "allow"
  }
}
```

## 3. 构建与安装

1. 安装 [Android Studio](https://developer.android.com/studio)（含 Android SDK）。
2. 用 Android Studio 打开本目录（`File > Open` 选择项目根目录），等待 Gradle 同步完成。
3. 连接手机（开启开发者选项 + USB 调试），点击 **Run**。
   - 或 `Build > Build APK(s)` 后手动安装。
4. 首次启动授予**通知权限**；建议在系统设置里把本 App 加入**电池优化白名单**（防止后台被休眠）。

> 环境要求：JDK 17+，Android SDK Platform 35。若本机缺少 Gradle wrapper 的 `gradle-wrapper.jar`，在 Android Studio 中打开即可自动补全，或执行 `gradle wrapper`。

## 4. 使用步骤

1. 打开 App，填写电脑的局域网 IP（如 `192.168.1.100`）、端口 `4096`，如有密码一并填写。
2. 点击 **保存并连接**。状态卡片显示「已连接」即成功。
3. 之后电脑上 opencode 触发权限请求 / 提问 / 完成 / 失败，手机会弹出对应通知：
   - **权限请求**：通知上直接点「允许一次 / 总是允许 / 拒绝」。
   - **问题**：点通知进入全屏答题页，选择选项或填写自定义答案后「提交答案」。
4. 事件日志会记录所有连接状态与事件，便于排查。

## 5. 技术说明

- 纯局域网直连：手机通过 **SSE（`GET /global/event`）** 订阅 opencode 事件，通过 REST 回传决定/答案，无需云服务。
- 后台/锁屏：**前台服务**（`dataSync` 类型）保持长连接，权限/问题通知使用高优先级 + 全屏 Intent。
- 断线自动重连（指数退避 1s → 30s）。

### 接口说明（针对 opencode 版本差异做容错）

- 权限回复：依次尝试 `POST /session/{id}/permissions/{permissionID}`（`{response}`）、`POST /api/permission/{id}/reply`（`{reply}`）、`POST /permission/{id}/reply`。
- 问题回复：依次尝试 `POST /api/question/{id}/reply`、`POST /question/{id}/reply`（`{answers}`）；跳过用对应 `reject` 端点。
- 事件类型同时兼容 `permission.updated`（旧）与 `permission.asked`（新）。

若你本机的 opencode 版本路径不同，可在 `net/OpencodeApi.kt` 中调整候选路径。

## 6. 已知限制

- 若电脑端 TUI 同时在操作，控制请求可能被 TUI 抢先处理（典型「人离开电脑」场景不受影响；权限走独立回复接口，无此问题）。
- Android 15+ 对 `dataSync` 前台服务有 6 小时限制（个人工具足够）。
