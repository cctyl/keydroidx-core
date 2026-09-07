# 响应式原生 DP 布局规范（分辨率适配唯一事实源）

> **全生态唯一事实源**。所有页面布局、尺寸取值、分辨率适配问题一律以此文为准。
> 历史演进与废弃方案见 [NOKIA_DEVELOPMENT_RULES.md](../NOKIA_DEVELOPMENT_RULES.md) §双分辨率适配规范（其中「根布局写死 240dp」一条**已废止**）。

---

## 一、给设计者的结论（先读这段）

**你的 240×320 设计稿依然是基准，没有作废。**

作废的是「用**运行时拉伸**去适配更大屏幕」这个实现手段，不是 240×320 这个设计网格。

| | 旧方案（已废弃） | 现行方案 |
|:---|:---|:---|
| 设计基准 | 240×320 dp | **240×320 dp（不变）** |
| 根布局宽度 | 写死 `240dp` | `match_parent` |
| 适配手段 | 运行时 `setScaleX/setScaleY` 整体放大 | **不做缩放**，靠弹性布局自然填充 |
| 元素尺寸 | 全部按 240dp 画布写死，再被整体乘 scale | 固定区按 dp 定值，**弹性区用 `weight` 均分** |

**出设计稿时要标注的**：固定尺寸元素（图标、行高、栏高、圆角）照旧给具体 dp；弹性元素（列表行宽、网格列宽、分隔线、背景）标「撑满」，**不要标宽度数值**。

---

## 二、为什么废弃旧方案

旧方案是「240×320 dp 基准 + 运行时 GPU `setScaleX/setScaleY` 矩阵拉伸」，在 240×320 上正常，在 320×480 及以上有两个无法克服的物理缺陷：

1. **GPU 离屏贴图二次插值导致全局模糊**
   `View.setScaleX/Y` 会先把 240dp 视口内容绘制到一块低分辨率离屏纹理上，GPU 再把它放大 1.33~1.5 倍并应用双线性插值，导致矢量图标、文字边缘、点线分隔线全部发虚。

2. **宽高比差异导致元素被纵向拔高**
   240×320 是 3:4（0.75），320×480 是 2:3（0.667）。为避免底部露白，旧架构引入 `fixMidContentHeight` 把高度强行撑大为 `panelH / scale`，使中间区域图标和间距被纵向扯高。

> 完整剖析见 `keydroidx-launcher/docs/响应式原生dp布局与多分辨率架构设计文档.md` §一（该文档已标记为历史设计文档，内容仍可参照）。

---

## 三、现行方案：三条铁律

### 铁律 1：根布局一律 `match_parent`，禁止写死宽度

```xml
<!-- ✅ 正确 -->
android:layout_width="match_parent"
android:layout_height="match_parent"

<!-- ❌ 错误：已废止 -->
android:layout_width="240dp"
```

**代码现状（2026-09 核实）**：launcher 48 个布局、music 23 个布局中 `layout_width="240dp"` 均为 **0 处**。残留的 240dp 字样全是注释。

### 铁律 2：弹性区用 `weight` 均分，禁止用计算出的固定宽度

```java
// 3 列网格：宽度 0 + weight 1
cell.setLayoutParams(new LinearLayout.LayoutParams(0, cellHeight, 1f));
```

参考实现：`KeydroidxMenuFragment.java:867`（列数 `COLS = 3`）。

**元素较少时可退化为固定宽度 + 横向滚动**，参考 `KeydroidxDesktopFragment.java:779`：

```java
boolean useWeight = count <= 4;          // ≤4 个：均分铺满
int fixedCellWidth = KeydroidxDimens.dp(res, 48);
// >4 个：固定 48dp 单元格，外层 HorizontalScrollView 横滚
```

### 铁律 3：固定区用 dp 定值，不随屏幕宽度变化

顶栏、底栏、快捷栏、开关栏、图标、圆角等一律给 dp 定值，由框架保证物理尺寸一致。

---

## 四、density 吸附与字体缩放锁定

由 `common` 的 `KeydroidxBaseActivity.attachBaseContext()` 统一处理，**所有页面自动生效，业务代码不要干预**。

### 4.1 densityDpi 吸附

把 `Configuration.densityDpi` 吸附到标准档位 `{120, 160, 213, 240, 320, 480, 640}`：

- `densityDpi < 160` → 强制 **160**
- 不在档位内 → 取**最接近**的档位
- 已在档位内 → 不变

**为什么是这几个档位**：它们让各种物理分辨率都落回约 **240dp 的逻辑宽度**：

| 物理宽度 | 吸附后 densityDpi | 逻辑宽度 |
|:---|:---|:---|
| 240px | 160 | 240dp |
| 320px | 213 | ≈240dp |
| 480px | 320 | 240dp |
| 720px | 480 | 240dp |

**副作用（也是目的）**：低分辨率设备（如 320×480 实测 136 DPI → density 0.85）的所有 dp 尺寸会落在亚像素位置被抗锯齿虚化。吸附到 1.0（mdpi）后所有尺寸对齐整数像素，消除模糊。物理布局完全不变，240dp 设计仍铺满屏幕。

### 4.2 fontScale 锁定为 1.0

```java
newConfig.fontScale = 1.0f;
```

> ⚠️ **禁止双重缩放**：绝对不要在 `attachBaseContext()` 里再把 `fontScale` 设成用户倍率。
> 用户字号调节走 `KeydroidxFontManager.setFontScale()`（作用于 `KeydroidxFontManager` 自身，不经过 `Configuration`），见 [typography-and-font-spec.md](./typography-and-font-spec.md)。

### 4.3 getScale() 恒为 1.0

`KeydroidxBaseActivity.getScale()` 在原生布局模式下返回 `1.0f`，`scaleMidContent()` / `applyScale()` 不再对 View 做任何 `setScaleX/Y` 变换。

> 保留 `getScale()` 只为兼容少量历史调用，**新代码不要依赖它**。

---

## 五、固定区 / 弹性区判定

| 元素 | 类型 | 取值 |
|:---|:---|:---|
| 页面根布局 | 弹性 | `match_parent` |
| 顶栏 `topPanel` | 固定 | `wrap_content` 原生渲染 |
| 底栏（软键条）`bottomPanel` | 固定 | 高度 **24dp**，宽度 `match_parent` |
| 中间面板 `midPanel` | 弹性 | `match_parent`，承载各 Fragment |
| 网格单元格（≤4 项） | 弹性 | 宽 `0dp` + `weight=1` |
| 网格单元格（>4 项） | 固定 | 宽 48dp + 横向滚动 |
| 列表行 | 弹性 | `match_parent`，左右 padding 8dp |
| 分隔线 | 弹性 | 横向撑满 |
| 图标 | 固定 | 按设计稿给 dp |

> 底栏 24dp 已核实：`keydroidx_bottom_bar.xml:15`。
> 分隔线实现见 `KeydroidxDashedLineDrawable`。

---

## 六、验收标准

新界面或改造后的界面，必须在以下档位逐项走查：

| 档位 | 要求 |
|:---|:---|
| **240×320 @120dpi**（QVGA） | 界面无缝铺满，文字和图标保持 1:1 像素级复古质感，无错位与溢出 |
| **320×480 @136dpi**（HVGA） | 文字、点线、状态栏图标**绝对锐利、零发虚**；图标为 1:1 正方形，**无纵向拉伸**；焦点框与滚动正常 |
| **480×800**（WVGA）及以上 | 布局不崩、不变形、不裁切、可正常操作 |

**关键判据**：320×480 上点线分隔线必须清晰可见（不消失、不变实线）——这是旧方案下最容易暴露的问题。

---

## 七、相关文档

- 字号与字体 → [typography-and-font-spec.md](./typography-and-font-spec.md)
- 页面基类与滚动 → [../guide/05-page-framework.md](../guide/05-page-framework.md)
- 接入红线总表 → [../guide/00-development-redlines.md](../guide/00-development-redlines.md)
- 历史设计文档（作废方案剖析）→ `keydroidx-launcher/docs/响应式原生dp布局与多分辨率架构设计文档.md`
