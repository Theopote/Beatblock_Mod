# Animation Library 拖拽 UX：Preset + Target + Time

> 状态：**设计锁定（先不扩功能）**  
> 相关实现：`TimelineAudioDropHandler.handleAnimationPresetDrop`、`EventLibraryPanelPresenter.resolveTargetObjectId`、`AnimationLibraryPanelPresenter`

## 1. 为什么先停功能

Animation Library 已具备：搜索、按影响维度分组、收藏、应用到选中事件、拖到 Timeline、Presenter、Toast。方向正确。

下一步**不要**继续堆入口（更多 preset、更多快捷按钮），先统一语义：

> **「拖一个动画」到底代表什么？**

若语义不清，每加一个入口都会放大「不确定感」。

## 2. 核心公式（权威）

与架构第 2 层 `StageEvent` 对齐：

```
Preset  +  Target  +  Time  =  StageEvent
（做什么） （对谁）   （何时）   （时间轴上的一条权威事件）
```

**禁止**把产品语义理解成：

```
Preset + Time  →  隐式猜 Target
```

隐式猜测可以当**快捷默认**，但不能是**唯一解释**；用户必须随时能回答：

> 我把 Bounce 拖上去，它作用在哪些方块上？

## 3. 现状（问题诊断）

当前拖 preset → 动画轨（`TimelineAudioDropHandler`）的 Target 推断：

```
1. 优先：时间轴上「当前选中动画事件」的 targetObjectId
2. 否则：host.resolveDefaultTargetObjectId()（宿主默认，通常第一个/主 StageObject）
3. 否则：失败 → Toast「无舞台对象」，不创建事件
```

| 优点 | 缺点 |
|------|------|
| 早期版本能跑通 | 同一操作在不同选中状态下结果不同，难预测 |
| 实现简单 | 世界选区 / 多 StageObject 未进入决策 |
| | **完全拒绝**无 target 的创建 → 像表单校验，不像 AE/Sequencer |
| | 用户无法先落事件再补绑定 |

Event Library「应用模板」走同一类 `resolveTargetObjectId`，问题同源。

「应用到选中事件」路径（改已有事件的 preset）语义清晰，**不在本问题范围**；本设计专指 **新建**（拖入时间线 / 从库生成新 clip）。

## 4. 目标交互：三种拖拽状态

拖放落点给出 **Time**；**Target** 由世界/舞台选区与用户选择决定。

### 4.1 单 StageObject 选中（世界或舞台主选）

```
Bounce  →  Timeline
→ 自动：Target = 当前唯一选中 StageObject
→ 创建已绑定 StageEvent
→ Toast：已应用到「对象名」
```

### 4.2 多个 StageObject 选中

**不静默只取第一个。** 弹出选择（或等价的轻量菜单）：

```
应用到：
  ○ 当前主对象          → 1 条事件，Target = primary
  ○ 所有选中对象        → N 条事件，同一 Time + Preset，各绑各 Target
  ○ 创建 Group Event     → 1 条组事件（或组 id 绑定；实现可后置）
```

默认推荐：**所有选中对象**（创作者多选意图通常是「都做这个动作」）。

### 4.3 没有 StageObject 选中

**允许创建，不拒绝：**

```
时间线上生成 UNBOUND EVENT
  Preset = Bounce
  Time   = drop time
  Target = 空 / 显式 unbound 标记
```

视觉（Timeline / 属性面板）：

- 红色或黄色徽章：**未绑定舞台对象**
- 播放：跳过或 no-op + 可选警告（产品二选一，默认 **跳过并记一次 warn**，避免崩播放）
- 绑定方式：属性面板选 StageObject、或拖 StageObject 到该事件、或世界选中后「绑定到选中」

这才是 AE / Sequencer 式：**先落点，再补绑定**，而不是「必须先填表再允许创建」。

## 5. 与现有模型的映射

| 概念 | 代码落点 |
|------|----------|
| Preset | `BlockInfluencePreset` / `animationTypeId` |
| Time | `TimelineAnimationEvent.timeSeconds`（drop + snap） |
| Target | `targetObjectId`；空串 = unbound（今日已允许存空，但创建路径会拒） |
| StageEvent | `TimelineAnimationEvent`（第 2 层） |

### 建议的解析 API（实现时抽出，禁止再复制粘贴推断）

```text
enum DropTargetMode {
  SINGLE_SELECTED,      // 唯一选中 → 自动绑
  MULTI_CHOICE_NEEDED,  // 多选 → UI 决策
  UNBOUND               // 无选中 → 允许空 target
}

record ResolvedDropTargets(DropTargetMode mode, List<String> stageObjectIds, String primaryId);

ResolvedDropTargets resolveForAnimationDrop(WorldSelection, StageObjectSystem, TimelineSelection);
```

拖放 handler 只做：

1. `resolveForAnimationDrop`
2. 按 mode 自动创建 / 弹窗 / 写 unbound
3. 统一 Toast / 事件外观

## 6. 明确不做（本阶段）

- 不新增更多 Animation Library 功能入口
- 不扩大 preset 数量作为「进度」
- 不在未统一 Target 语义前做复杂批量绑定工具栏
- Group Event 的完整数据模型可后置；多选阶段先用「N 条独立事件」也可验收

## 7. 实现顺序（待开工时）

1. **文档与测试约定**：unbound 事件允许写入；UI 有 unbound 状态（黄/红）
2. **抽出** `AnimationDropTargetResolver`（单测覆盖：0 / 1 / N 选中）
3. **改 drop 路径**：失败改 unbound，不再 hard-fail
4. **单选自动绑**：以**世界/舞台选中 StageObject** 为优先，其次才是「选中事件的 target」（今天顺序反了，偏编辑器内部状态）
5. **多选对话框**（最后做 UI）
6. Event Library 应用模板对齐同一 resolver

## 8. 验收标准

- [ ] 无 StageObject 时拖 Bounce 仍生成事件，并显示「未绑定」
- [ ] 单选 StageObject A 时拖 Bounce，事件 `targetObjectId == A`，Toast 可读
- [ ] 多选 A+B 时不静默只绑 A；必须经用户选择（或明确默认「全部」且可撤销）
- [ ] 属性面板可把 unbound 事件绑到任意 StageObject
- [ ] 播放 unbound 不崩溃

## 9. 一句话

**拖动画 = 在时间上承诺一个动作；对象可以当时就定，也可以后补。**  
不要把 BeatBlock 做成「必须先选对象才能拖」的表单。
