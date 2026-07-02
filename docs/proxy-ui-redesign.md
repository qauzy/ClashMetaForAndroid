# Proxy UI 改造设计文档 — Clash Verge 风格

## 1. 目标

将当前 ClashMetaForAndroid 的代理页面改造为类似 Clash Verge 的样式和交互体验。

## 2. 当前状态 vs 目标状态

### 2.1 当前 ClashMetaForAndroid

| 特性 | 实现方式 |
|------|----------|
| 组切换 | ViewPager2 + TabLayout 横向滑动 |
| 代理项渲染 | 自定义 Canvas View (ProxyView) |
| 布局模式 | 网格 1/2/3 列 |
| 延迟显示 | 纯文字，无颜色区分 |
| 延迟测试 | FAB 按钮，手动触发 |
| UI 框架 | XML + DataBinding + 自定义 View |
| 主题 | Material 2 |

### 2.2 目标 Clash Verge 风格

| 特性 | 目标 |
|------|------|
| 组切换 | 单个滚动列表，组头可折叠展开 |
| 代理项渲染 | Card 风格，圆角 + 阴影 |
| 布局模式 | 组内 2 列网格 |
| 延迟显示 | 彩色标签：绿(<200ms) / 黄(<500ms) / 红(>=500ms) |
| 延迟测试 | 自动周期性检测 + 手动触发 |
| UI 框架 | 保持 XML + DataBinding（或局部迁移到 Compose） |
| 主题 | 升级到 Material 3 |

## 3. 架构改动方案

### 方案 A：局部重构（推荐，13-18人日）

在现有架构内改进，保持 XML + DataBinding + RecyclerView 体系。

### 方案 B：全量 Compose 迁移（不推荐，30-45人日）

重写整个页面为 Jetpack Compose。风险高，与现有架构割裂。

**推荐方案 A**，理由：
- 与现有代码风格一致
- 渐进式改造，可逐步上线
- 风险低，可回退

## 4. 详细设计方案

### Phase 1：代理项视觉改造（3-5人日）

#### 4.1.1 延迟颜色编码

新增延迟阈值工具类 `ProxyDelayColor.kt`：

```kotlin
// design/src/main/java/com/github/kr328/clash/design/util/ProxyDelayColor.kt
object ProxyDelayColor {
    const val TIMEOUT = 9999L
    fun getColor(delay: Long, context: Context): Int {
        return when {
            delay <= 0 || delay > Short.MAX_VALUE -> Color.GRAY          // 超时/未测试
            delay < 200 -> Color.GREEN
            delay < 500 -> Color.YELLOW
            else -> Color.RED
        }
    }

    fun getBackgroundColor(delay: Long, context: Context): Int {
        return when {
            delay <= 0 || delay > Short.MAX_VALUE -> Color.TRANSPARENT
            delay < 200 -> ContextCompat.getColor(context, R.color.delay_good_bg)
            delay < 500 -> ContextCompat.getColor(context, R.color.delay_medium_bg)
            else -> ContextCompat.getColor(context, R.color.delay_bad_bg)
        }
    }
}
```

新增颜色资源 `colors.xml`：

```xml
<color name="delay_good_bg">#1A4CAF50</color>
<color name="delay_medium_bg">#1AFF9800</color>
<color name="delay_bad_bg">#1AF44336</color>
```

#### 4.1.2 ProxyView 重构

修改 `ProxyView.kt`：
- 延迟显示区域改为圆角 Chip/Badge 背景
- 选中态使用更明显的 Material You 风格高亮
- 增加节点的类型图标（Shadowsocks/VMess/Trojan 等）
- 适配 2 列卡片模式的圆角和阴影

修改 `ProxyViewConfig.kt`：
- 增加延迟颜色配置项
- 增加类型图标配置项
- 增加卡片圆角半径、阴影高度

修改 `ProxyViewState.kt`：
- 增加延迟颜色计算逻辑
- 增加图标资源解析

### Phase 2：布局结构改造（5-8人日）

#### 4.2.1 替换 ViewPager2 为 NestedScrollView + 可折叠组头

**核心思想**：将 ViewPager2 的横向滑动 + TabLayout 替换为单列纵向滚动的 `NestedScrollView`（或 `RecyclerView`），每个组是一个可折叠的卡片组。

**布局文件** `design_proxy.xml`：

```xml
<layout>
    <androidx.coordinatorlayout.widget.CoordinatorLayout>
        <com.google.android.material.appbar.AppBarLayout>
            <androidx.appcompat.widget.Toolbar />
        </com.google.android.material.appbar.AppBarLayout>

        <!-- 替换 ViewPager2 为纵向列表 -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/proxy_list"
            android:nestedScrollingEnabled="true" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton />
    </androidx.coordinatorlayout.widget.CoordinatorLayout>
</layout>
```

#### 4.2.2 组头设计

新的组头 View `ProxyGroupHeaderView.kt`：
- 左侧：组名 + 类型标签（Selector/URLTest/Fallback/LoadBalance）
- 右侧：当前选中节点名 + 展开/折叠箭头
- 点击组头切换展开/折叠

布局 `item_proxy_group_header.xml`：

```xml
<layout>
    <com.google.android.material.card.MaterialCardView>
        <LinearLayout horizontal>
            <TextView android:text="@{group.name}" />
            <Chip android:text="@{group.type}" />
            <Space />
            <TextView android:text="@{group.now}" />
            <ImageView android:rotation="@{expanded ? 180 : 0}" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</layout>
```

#### 4.2.3 新适配器

替换 `ProxyPageAdapter` + `ProxyPageFactory` → 统一为 `ProxyGroupAdapter.kt`：

```kotlin
class ProxyGroupAdapter(
    private val groups: List<ProxyGroupUI>,
    private val onSelect: (groupIndex: Int, proxyName: String) -> Unit,
    private val onTest: (groupIndex: Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PROXY = 1
    }

    override fun getItemViewType(position: Int): Int { ... }
    override fun getItemCount(): Int { ... } // 头部数 + 展开组的代理数
}
```

#### 4.2.4 数据模型调整

新增 `ProxyGroupUI.kt`（UI 层状态模型）：

```kotlin
data class ProxyGroupUI(
    val index: Int,
    val name: String,
    val type: String,
    val now: String,
    val proxies: List<ProxyUI>,
    val isExpanded: Boolean = true,
    val isTesting: Boolean = false,
)

data class ProxyUI(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val delay: Long,
    val delayColor: Int,
    val icon: Drawable?,
)
```

### Phase 3：功能增强（3-5人日）

#### 4.3.1 自动延迟检测

在 `ProxyActivity.kt` 中增加定时任务：

```kotlin
// 每 60 秒自动检测延迟
private val autoTestJob = lifecycleScope.launch {
    while (isActive) {
        delay(60_000)
        if (isResumed) {
            sendRequest(ProxyDesign.Request.AutoUrlTest)
        }
    }
}
```

#### 4.3.2 搜索/过滤

在 Toolbar 增加搜索按钮，展开为 `SearchView`：

```kotlin
// ProxyDesign.kt
sealed class Request {
    data class Search(val query: String) : Request()
    object ClearSearch : Request()
}
```

搜索时过滤 `ProxyGroupUI.proxies` 列表，匹配 `name` 或 `title`。

#### 4.3.3 Provider 信息展示

在组头下方增加 Provider 来源信息（可选）：

```xml
<TextView
    android:text="@{providerName}"
    android:textSize="12sp"
    android:textColor="@color/text_secondary" />
```

### Phase 4：打磨（2-3人日）

- 组展开/折叠动画（LayoutAnimation 或 ItemAnimator）
- 延迟变化动画（颜色过渡动画）
- 延迟测试进度指示器
- 空状态/加载状态
- 暗色模式适配
- 触摸反馈优化

## 5. 文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `design/.../util/ProxyDelayColor.kt` | 延迟颜色工具类 |
| `design/.../component/ProxyGroupHeaderView.kt` | 可折叠组头 View |
| `design/.../adapter/ProxyGroupAdapter.kt` | 统一列表适配器 |
| `design/.../model/ProxyGroupUI.kt` | UI 层数据模型 |
| `design/.../model/ProxyUI.kt` | UI 层代理项模型 |
| `design/res/layout/item_proxy_group_header.xml` | 组头布局 |
| `design/res/layout/item_proxy_group_footer.xml` | 组尾（Provider 信息等）|

### 修改文件

| 文件 | 改动 |
|------|------|
| `design/.../component/ProxyView.kt` | 延迟 Chip 渲染、类型图标、选中态 |
| `design/.../component/ProxyViewConfig.kt` | 颜色/圆角/阴影配置 |
| `design/.../component/ProxyViewState.kt` | 延迟颜色状态 |
| `design/.../ProxyDesign.kt` | 请求类型扩展、新数据处理 |
| `design/.../adapter/ProxyPageAdapter.kt` | 废弃或简化 |
| `design/.../adapter/ProxyAdapter.kt` | 废弃或简化 |
| `design/.../component/ProxyPageFactory.kt` | 废弃或简化 |
| `design/res/layout/design_proxy.xml` | ViewPager2 → RecyclerView |
| `app/.../ProxyActivity.kt` | 自动检测、搜索功能 |
| `design/.../store/UiStore.kt` | 搜索历史、自动检测开关 |
| `design/res/values/colors.xml` | 延迟颜色资源 |
| `design/res/values/dimens.xml` | 圆角/间距尺寸 |

## 6. 工作量评估

| Phase | 内容 | 人日 |
|-------|------|------|
| P1 | 代理项视觉改造 | 3-5 |
| P2 | 布局结构改造 | 5-8 |
| P3 | 功能增强 | 3-5 |
| P4 | 打磨 | 2-3 |
| **合计** | | **13-21** |

**风险点**：
- 组折叠/展开动画在 RecyclerView 中可能需要配合 `notifyItemRangeInserted/Removed`
- 原有 ViewPager2 的横向手势与纵向 RecyclerView 可能存在冲突
- 适配器替换后需要重写 `ProxyDesign` 中的部分逻辑
- 需要保留 UiStore 中的用户偏好兼容性

## 7. 可选优化方向（超出本次范围）

- Compose 逐步引入（对新页面或组件使用 Compose）
- M3 动态主题色支持
- 拖拽排序
- 代理组批量延迟测试
- 图标包支持

## 8. 验收标准

- [ ] 代理项显示延迟 Chip 颜色编码（绿/黄/红/灰）
- [ ] 组可折叠展开，默认展开
- [ ] 单列纵向滚动替代 ViewPager2 横向滑动
- [ ] 点击代理项切换选择正常
- [ ] 手动延迟测试正常
- [ ] 搜索/过滤功能正常
- [ ] 兼容现有 Material 2 主题
- [ ] 暗色模式正常
