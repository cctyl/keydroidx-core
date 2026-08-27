# KeydroidX Core 文档索引

> 本目录是 `nokia-key-core` SDK 的完整参考文档，按模块拆分，API 细化到每个公开方法。
> 快速了解请看根目录 [README.md](../README.md)；开发规范与踩坑记录见 [NOKIA_DEVELOPMENT_RULES.md](../NOKIA_DEVELOPMENT_RULES.md)。

## 📚 目录

| # | 文档 | 内容 | 核心类 |
|---|------|------|--------|
| 01 | [快速接入](./01-getting-started.md) | 依赖引入、包可见性声明、最小可运行示例、构建注意事项 | — |
| 02 | [生态客户端（配置同步）](./02-client.md) | 跨进程配置读取、三级降级、热同步监听、主题/字体当前值管理 | `NokiaClient` `NokiaKeyClient` |
| 03 | [按键模型](./03-key-model.md) | 语义动作常量、键值映射表、本地持久化、KeyEvent 解析 | `NokiaKeyAction` `NokiaKeyBinding` |
| 04 | [页面骨架 Activity](./04-base-activity.md) | 统一顶栏/软键栏骨架、按键分发、主题字体自动应用、电量广播 | `NokiaBaseActivity` |
| 05 | [页面框架（Fragment）](./05-page-framework.md) | 页面契约接口与三种页面基类（普通页 / 列表页 / 滚动页） | `NokiaPage` `NokiaPageHost` `NokiaFocusHost` `NokiaPageFragment` `NokiaListPageFragment` `NokiaScrollPageFragment` |
| 06 | [列表焦点控制](./06-list-focus.md) | 循环导航、高亮、防出界滚动，独立于继承体系的组合式控制器 | `NokiaListFocusHelper` |
| 07 | [标准弹窗](./07-dialogs.md) | 选项菜单 / 确认 / 输入三种复古弹窗与弹窗焦点修复 | `NokiaOptionsDialog` `NokiaConfirmDialog` `NokiaInputDialog` `NokiaDialogFocus` |
| 08 | [主题 · 字体 · 图标](./08-theme-font-icons.md) | 六套主题定义、点阵字体管理、MaterialIcons 矢量图标、电池/虚线 Drawable、尺寸工具 | `NokiaTheme` `NokiaFontManager` `NokiaIcons` `NokiaBatteryDrawable` `NokiaDashedLineDrawable` `NokiaDimens` |
| 09 | [配键向导](./09-key-wizard.md) | 独立运行的九键录入向导 Activity | `NokiaKeyWizardActivity` |
| 10 | [反馈上报](./10-feedback.md) | 内置反馈页、日志打包、Ed25519 签名上传、密钥管理约定 | `NokiaFeedback` `NokiaFeedbackActivity` `KdfbUploader` |
| 11 | [宿主应用开发规范](./HOST_APP_DEVELOPMENT_SPEC.md) | 打造纯正诺基亚风格的强制类继承、UI 渲染、按键分发与交互规范 | `NokiaBaseActivity` `NokiaListPageFragment` `NokiaTheme` |

## 📐 架构与设计（延伸阅读）

| 文档 | 内容 |
|------|------|
| [HOST_APP_DEVELOPMENT_SPEC.md](./HOST_APP_DEVELOPMENT_SPEC.md) | **宿主应用必读**：全面遵守诺基亚复古风格的架构与编码硬性规范 |
| [PAGE_ARCHITECTURE.md](./PAGE_ARCHITECTURE.md) | 页面框架的设计理念：契约接口、模板基类、焦点管理的来龙去脉 |
| [按键机生态与独立应用扩展架构设计](./按键机生态与独立应用扩展架构设计.md) | 生态整体架构：桌面 / core / 独立应用的分工与协同 |

## 🗺️ 模块关系图

```
NokiaClient (单例：三级降级 + ContentObserver 热同步)
    │ 提供 keyBinding / themeId / fontId
    ▼
NokiaBaseActivity (统一骨架：顶栏 + 内容区 + 软键栏)
    │ 实现 NokiaPageHost；通过 getCurrentPage() 找到前台 NokiaPage 分发按键
    ▼
NokiaPage 契约 ── NokiaPageFragment (模板基类)
                    ├── NokiaListPageFragment   单列列表页（内置循环导航）
                    ├── NokiaScrollPageFragment 长内容滚动页
                    └── 任意业务 Fragment / Activity

独立组件（不依赖继承体系）：
NokiaListFocusHelper ─── 列表焦点控制器（Activity 内直接用）
NokiaOptionsDialog / NokiaConfirmDialog / NokiaInputDialog ─── 标准弹窗
NokiaTheme / NokiaFontManager / NokiaIcons ─── 视觉三件套
```

## 🧭 我该从哪篇看起？

- **新接入一个生态应用** → 01 → 04 → 05
- **只想给现有 Activity 加物理按键** → 03 → 02
- **做一个带列表的设置页** → 05 → 06
- **要弹菜单/确认框** → 07
- **换肤 / 字体缩放 / 加图标** → 08
- **设备没装桌面，需要本机配键** → 09
