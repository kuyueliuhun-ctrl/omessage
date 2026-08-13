# OpenCode 通知（omessage）

当电脑上的 opencode 触发以下事件时，在手机弹窗通知（支持后台/锁屏）：

| 事件 | 说明 | 手机端可交互 |
|---|---|---|
| 权限请求 (`permission.asked`) | opencode 需要批准执行某操作 | 「允许一次 / 总是允许 / 拒绝」 |
| 问题抛出 (`question.asked`) | AI 执行中向用户提问 | 全屏作答 / 跳过 |
| 执行完成 (`session.idle`) | 任务完成 | 仅提示 |
| 执行失败 (`session.error`) | 任务出错 | 仅提示 |

## 工作原理

```
[电脑] opencode (TUI/桌面版/serve)
        └── 插件 omessage.ts 监听事件 → POST 到 ntfy.sh/<主题>
                                              │
[手机] App 订阅 ntfy.sh/<主题> (SSE) ──────────┘
        └── 弹窗通知；「允许/拒绝/作答」时 POST 回电脑 opencode 服务器
```

> 为什么用 ntfy.sh 中转：opencode 的权限/提问走「单消费者控制队列」，桌面版客户端独占它，手机作为第二个客户端无法直接订阅到这些事件。插件跑在 opencode 进程内、能直接监听事件，再经 ntfy 推送到手机。

## 1. 电脑端：安装插件

1. 把本仓库根目录的 `omessage.ts` 复制到插件目录（二选一）：
   - 全局：`~/.config/opencode/plugins/omessage.ts`（Windows：`C:\Users\<你>\.config\opencode\plugins\omessage.ts`）
   - 项目级：项目根目录 `.opencode/plugins/omessage.ts`
2. 设置主题（随机长字符串，与手机 App 保持一致）并重启 opencode：

```powershell
# Windows (PowerShell)
$env:OMESSAGE_TOPIC = "omessage-a1b2c3d4e5f6"
opencode serve --hostname 0.0.0.0 --port 4096   # 或直接运行 opencode TUI / 桌面版

# macOS / Linux
export OMESSAGE_TOPIC="omessage-a1b2c3d4e5f6"
```

> - 可选：自建 ntfy 时设置 `OMESSAGE_NTFY_URL`（默认 `https://ntfy.sh`）。
> - 电脑端 opencode 服务器需让手机能访问（用于回复），启动时加 `--hostname 0.0.0.0 --port 4096`，防火墙放行 4096。

## 2. 手机端：构建与使用

1. 用 Android Studio 打开本目录，Run 安装。
2. 首次启动授予通知权限，建议加入电池优化白名单。
3. 在 App 设置页填写：
   - **主机 / 端口**：电脑的局域网 IP 与 opencode 端口（用于回复）
   - **用户名 / 密码**：如设置了 `OPENCODE_SERVER_PASSWORD`
   - **ntfy 主题**：与电脑插件 `OMESSAGE_TOPIC` 一致
4. 点击「保存并连接」。之后 opencode 的四种事件都会推送到手机：
   - 权限请求 → 通知上直接点「允许一次 / 总是允许 / 拒绝」
   - 问题 → 点通知进入全屏答题页

## 3. 技术说明

- 推送链路：opencode 插件 → ntfy.sh（SSE 订阅），手机 App 用**前台服务**（`dataSync`）保持 ntfy 连接。
- 回复链路：手机 App 直接 POST 到电脑 opencode 服务器（局域网），依次尝试 `POST /api/session/{sid}/permission/{id}/reply`、`POST /permission/{id}/reply`（`{reply}`）；问题用 `POST /api/session/{sid}/question/{id}/reply`（`{answers}`）。
- 断线自动重连（指数退避 1s → 30s）。

## 4. 已知限制

- 推送依赖互联网（ntfy.sh）；如纯内网环境，可自建 ntfy 并把 `OMESSAGE_NTFY_URL` 和 App 指向它。
- 「执行失败」依赖 `session.error` 事件；若 opencode 版本不同，可在 `omessage.ts` 中调整事件名。
