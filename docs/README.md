# KeydroidX Core 文档索引

> 本目录是 KeydroidX 生态 SDK（`keydroidx-common` + `keydroidx-key-core` + `keydroidx-mini-shizuku`）的完整参考文档。
> 快速了解请看根目录 [README.md](../README.md)。

## 阅读路线

- **新接入一个生态应用** → [00 接入红线（必读）](./guide/00-development-redlines.md) → [01 快速接入](./guide/01-getting-started.md) → [04 页面骨架](./guide/04-base-activity.md) → [05 页面框架](./guide/05-page-framework.md)
- **只想给现有 Activity 加物理按键** → [03 按键模型](./guide/03-key-model.md) → [02 生态客户端](./guide/02-client.md)
- **做一个带列表的设置页** → [05](./guide/05-page-framework.md) → [06 列表焦点](./guide/06-list-focus.md)
- **要弹菜单 / 确认框 / 全屏输入** → [07 标准弹窗](./guide/07-dialogs.md) → [08 文本输入](./guide/08-text-input.md)
- **换肤 / 字体缩放 / 加图标** → [09 主题·字体·图标](./guide/09-theme-font-icons.md)
- **设备没装桌面，需要本机配键** → [10 配键向导](./guide/10-key-wizard.md)
- **需要以 shell 身份执行系统命令** → [17 mini_shizuku 调用](./guide/17-mini-shizuku.md)
- **了解生态为什么长这样** → [architecture/](./architecture/)

---

## guide/ — 独立 App 接入指南

| # | 文档 | 内容 | 核心类 |
|---|------|------|--------|
| 00 | [接入红线（开发前必读）](./guide/00-development-redlines.md) | 硬性红线总表、软键栏规范、图标尺寸档位、自查清单 | — |
| 01 | [快速接入](./guide/01-getting-started.md) | 依赖引入、包可见性、最小可运行示例 | — |
| 02 | [生态客户端（配置同步）](./guide/02-client.md) | 跨进程配置读取、四级降级、热同步监听、主题/字体同步 | `KeydroidxClient` |
| 03 | [按键模型](./guide/03-key-model.md) | 语义动作常量、键值映射表、KeyEvent 解析与兜底 | `KeydroidxKeyAction` `KeydroidxKeyBinding` |
| 04 | [页面骨架 Activity](./guide/04-base-activity.md) | 统一顶栏/软键栏骨架、按键分发、主题字体自动应用 | `KeydroidxBaseActivity` |
| 05 | [页面框架（Fragment）](./guide/05-page-framework.md) | 页面契约接口与三种页面基类 | `KeydroidxPage` `KeydroidxPageHost` `KeydroidxFocusHost` `KeydroidxPageFragment` |
| 06 | [列表焦点控制](./guide/06-list-focus.md) | 循环导航、高亮、防出界滚动的组合式控制器 | `KeydroidxListFocusHelper` |
| 07 | [标准弹窗](./guide/07-dialogs.md) | 选项菜单 / 确认两种复古弹窗、弹窗按键解析机制 | `KeydroidxOptionsDialog` `KeydroidxConfirmDialog` |
| 08 | [文本输入](./guide/08-text-input.md) | 全屏文本编辑页（Fragment / Activity 双形态） | `KeydroidxTextInputFragment` `KeydroidxTextInputActivity` |
| 09 | [主题 · 字体 · 图标](./guide/09-theme-font-icons.md) | 主题定义、点阵字体管理、MaterialIcons 矢量图标、尺寸工具 | `KeydroidxTheme` `KeydroidxFontManager` `KeydroidxIcons` `KeydroidxDimens` |
| 10 | [配键向导](./guide/10-key-wizard.md) | 独立运行的九键录入向导 Activity | `KeydroidxKeyWizardActivity` |
| 11 | [反馈上报与日志](./guide/11-feedback.md) | 内置反馈页、日志打包、签名上传、统一日志器 | `KeydroidxFeedback` `KeydroidxLog` `FeedbackUploader` |
| 12 | [安装统计上报](./guide/12-install-stats.md) | 首装/升级各报一次、客户端幂等、与反馈共用配置 | `KeydroidxInstall` `InstallUploader` |
| 13 | [权限管理](./guide/13-permissions.md) | 统一权限门面，XXPermissions 多 ROM 适配，复古弹窗 | `KeydroidxPermissionManager` |
| 14 | [检查更新](./guide/14-update-check.md) | GitHub Release 版本对比、失败网盘兜底、复古弹窗 | `KeydroidxUpdateChecker` `KeydroidxUpdateDialog` |
| 15 | [关于页与更多应用](./guide/15-about-and-more-apps.md) | 关于页配置与「更多应用」推荐位 | `KeydroidxAboutConfig` `KeydroidxAboutFragment` |
| 16 | [开箱即用的 Activity](./guide/16-ready-made-activities.md) | 配键向导 / 文本输入 / 关于 / 反馈四个现成页面 | key-core 四个 Activity |
| 17 | [mini_shizuku 调用](./guide/17-mini-shizuku.md) | 零依赖 IPC 客户端：shell 命令执行、同签名鉴权、包可见性声明 | `MiniShizuku` |

## spec/ — 强制规范

| 文档 | 内容 |
|------|------|
| [typography-and-font-spec.md](./spec/typography-and-font-spec.md) | **字号排版唯一事实源**：6 级字号 Token、点阵字体规范、树缩放拦截 |
| [responsive-layout-spec.md](./spec/responsive-layout-spec.md) | **分辨率适配唯一事实源**：响应式原生 DP、densityDpi 吸附、240×320 设计基准说明 |

## architecture/ — 架构与决策

| 文档 | 内容 |
|------|------|
| [module-layering.md](./architecture/module-layering.md) | **现状说明**：四模块职责、key-core 真实构成、ThemeProvider 依赖倒置、接入方姿势 |
| [ecosystem-overview.md](./architecture/ecosystem-overview.md) | **演进愿景与跨应用协议**：拆生态动机、Provider 契约、音乐↔桌面 MediaSession 双通道 |

## reference/ — 第三方参考资料

| 文档 | 内容 |
|------|------|
| [FEATURE_PHONE_UI_SPEC.md](./reference/FEATURE_PHONE_UI_SPEC.md) | 功能机 UI 设计参考（英文，21 章，网上下载的第三方资料，未按本生态校准） |

## 根目录

| 文档 | 内容 |
|------|------|
| [NOKIA_DEVELOPMENT_RULES.md](./NOKIA_DEVELOPMENT_RULES.md) | **全生态硬性开发规范**：按键、弹窗、尺寸、日志等反复踩坑的经验沉淀 |

---

## 关键事实速查

| 事项 | 结论 |
|:---|:---|
| 仓库模块 | 4 个 Gradle 模块：`common`（全生态共享库）、`key-core`（Provider 客户端，api 依赖 common）、`mini-shizuku`（纯 IPC 零依赖）、`sample`（测试台）。坐标与依赖见 [module-layering](./architecture/module-layering.md) §二 / [AGENTS.md](../AGENTS.md) |
| 包名规则 | 通用能力一律 `io.github.cctyl.nokia.common.*`；`keycore.*` 下只剩 common 没有的 7 个类（见 [module-layering](./architecture/module-layering.md) §三）；原 16 个同名 `@Deprecated` 空壳桥接已于 2026-09-06 删除，`KeydroidxKeyClient` 兼容门面亦已删除。mini-shizuku 在 `io.github.cctyl.nokia.shizuku.*`（3 个类，独立零依赖） |
| 分辨率适配 | 响应式原生 DP，根布局 `match_parent` + `weight`，**禁止**运行时 `setScaleX/Y`，**禁止**根宽写死 240dp。240×320 仍是设计基准，见 [responsive-layout-spec](./spec/responsive-layout-spec.md) |
| 降级链路 | 正式 Provider → Debug Provider → 本地配置 → 默认键码（HOME 含 `debug` 时前两级顺序反转） |
| 日志 | 一律 `common.log.KeydroidxLog`，禁止 `android.util.Log`；落盘 `Android/data/<包名>/files/log/yyyyMMdd.log` |
| 上传鉴权 | 由 SDK 内部完成（协议不公开）；密钥来自宿主 BuildConfig，绝不入库 |
| 弹窗按键解析 | `KeydroidxUi.getKeyResolver()` → `KeyResolver`，不直接依赖 `KeydroidxKeyBinding` |
| 字号 | 6 级 Token：16/13/12/11/9/7 sp；**没有 8sp**；XML 禁止裸写字号 |
