# 11 · 字体与字号排版设计规范

本文档定义了 KeydroidX 按键机生态中 **基准 240×320 分辨率** 下的官方推荐字号、排版规范以及 SDK 的全自动字体缩放机制。

---

## 1. 核心设计原则

1. **基准视口原则（240×320）**：
   - 生态所有界面的设计基准分辨率为 **240dp × 320dp**。
   - `NokiaBaseActivity` 在初始化时会自动通过屏幕宽度比例将 DPI 规范化缩放至 240 视口，开发者在布局文件中编写的 `sp` / `dp` 均直接映射到该基准。
2. **点阵像素字体的细腻感**：
   - 像素点阵字体（如 Ark Pixel 12px / 16px、Unifont 等）自身具有极高的像素对齐度。
   - **禁止滥用 `android:textStyle="bold"`**：在点阵字体下加粗会直接使横纵像素加厚 1~2 倍，破坏复古细腻感并导致视觉过度膨胀。仅在大标题或特定高强调场景使用。
3. **字号层级克制**：
   - 屏幕尺寸通常为 2.4 / 2.8 英寸，界面应保证高信息密度且舒适可读。
   - 标准正文严守 **9sp ~ 10sp** 黄金区间，紧凑列表对齐桌面功能表。

---

## 2. 官方推荐字号阶梯 (Typography Tokens)

所有标准字号均已沉淀在 `nokia-common` 模块的 `res/values/dimens.xml` 中，推荐优先直接引用 Token：

| Token 名称 | 尺寸 (SP) | 适用场景与视觉层级 | 对标原生/桌面组件 |
| :--- | :--- | :--- | :--- |
| **`@dimen/nokia_font_large_title`** | **14sp ~ 16sp** | 极少使用：首屏超大数字时钟、核心主视觉大标题 | 桌面时钟大数字、关于页主应用名 |
| **`@dimen/nokia_font_title`** | **12sp ~ 13sp** | 页面大标题、软键中间主操作、弹窗标题栏 | `NokiaBaseActivity` 软键中键、弹窗 Title |
| **`@dimen/nokia_font_small_title`** | **11sp** | 左右软键文本、顶栏标题、二级突出按钮 | `tv_soft_left`、`tv_soft_right`、顶栏标题 |
| **`@dimen/nokia_font_body`** | **9sp ~ 10sp** | **正文基准**：列表主标题、歌名、搜索词、设置项名、功能表条目 | **桌面九宫格/列表功能表项**、标准 ListView 行 |
| **`@dimen/nokia_font_caption`** | **8sp** | **副文本**：歌手名、专辑名、列表序号、时间戳、二级辅助提示 | 桌面应用角标、歌曲辅助信息行 |
| **`@dimen/nokia_font_tiny`** | **7sp ~ 8sp** | 微型徽标、角标 Badge、极致紧凑状态信息 | 极小标签、下载进度百分比角标 |

---

## 3. 常见控件尺寸与间距推荐

为保证独立 App 与桌面 Launcher 交互体验的一致性，推荐使用以下尺寸参数：

| 控件类型 | 推荐尺寸/高度 | 推荐字号 | 备注 |
| :--- | :--- | :--- | :--- |
| **标准列表行 (List Row)** | `minHeight="36dp ~ 38dp"` | 主文本 9sp / 副文本 8sp | 左侧图标推荐 20dp，保持紧凑 |
| **快捷宫格卡片 (Grid Card)** | `height="42dp ~ 46dp"` | 标题 9sp / 描述 8sp | 卡片图标 20dp ~ 22dp |
| **分组小标题栏 (Section Header)**| `height="20dp ~ 22dp"` | 标题 8sp ~ 9sp | 不加粗，半透明背景 |
| **弹窗 (Dialog) 标题栏** | `height="28dp"` | 标题 12sp ~ 13sp | 背景为主题色渐变 |
| **弹窗 (Dialog) 内容行** | `minHeight="32dp ~ 36dp"` | 文本 10sp | 选中时圆角高亮 |

---

## 4. 全局自动字体与字号缩放机制

### 4.1 缩放机制说明
KeydroidX 生态采用 **“单一源头 (Single Source of Truth) + 自动树劫持”** 的字体缩放架构：
- 用户在桌面【设置】→【外观与显示】→【字体大小】中调节缩放倍率（如 1.0x, 1.25x, 1.5x）。
- `NokiaClient` 监听 Provider 广播并通知当前 Activity。
- `NokiaFontManager` 统一管理 `sFontScale` 与全局点阵 `Typeface`。

### 4.2 零手动负担：View 树自动拦截
从 `nokia-common` 升级后，SDK 在 `NokiaBaseActivity` / `NokiaFontManager` 层面内置了 **`ViewGroup.OnHierarchyChangeListener` 自动劫持**：
- **静态 XML 布局**：`NokiaBaseActivity` 初始化时自动遍历并应用缩放。
- **动态创建 View**：无论是业务层调用 `container.removeAllViews()` 重新 `inflate`，还是代码 `container.addView(tv)`，**底层的 HierarchyWatcher 都会在子节点挂载瞬间自动应用点阵 Typeface 并乘以 `sFontScale` 缩放**。
- **业务开发者无需手动编写任何 `NokiaFontManager.applyToViewTree` 代码**。

### 4.3 动态设置字号的安全 API
如果代码中需要动态为某个 `TextView` 设置字号，**请勿直接调用 `tv.setTextSize(sp)`**（这会破坏原始设计基准记录），而应使用 SDK 提供的专属助手方法：

```java
// 方式一：直接指定设计 SP 字号（自动记录基准并即时乘以 fontScale）
NokiaFontManager.setTextSize(myTextView, 9f);

// 方式二：直接引用 dimens.xml 资源
NokiaFontManager.setTextSizeResource(myTextView, R.dimen.nokia_font_body);
```

---

## 5. 常见排版避坑指南

1. **避免双重缩放**：
   - 严禁在宿主 `attachBaseContext` 中再次修改 `Configuration.fontScale`。
2. **避免大面积设置 `textStyle="bold"`**：
   - 现代字体的加粗是经过字形设计的，而点阵像素字体的加粗只是算法扩展，会导致像素糊在一起。9sp/10sp 字体请保持正常粗细。
3. **单行截断规范**：
   - 按键机屏幕宽度有限（240px），主标题务必配置 `android:singleLine="true"` 与 `android:ellipsize="end"`，配合焦点滚动（Marquee）提升视觉体验。