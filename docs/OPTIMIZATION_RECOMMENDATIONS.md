# BeatBlock 项目优化建议报告

> **生成日期**: 2026-06-21  
> **最近复核**: 2026-06-24（Timeline R5+、Presenter/DI、测试与文档对齐）  
> **分析范围**: 架构设计、代码质量、性能优化、工程化实践  
> **项目版本**: v1.0.0 (基于 commit ab40941)

---

## 目录

- [1. 执行摘要](#1-执行摘要)
- [2. 架构层面优化](#2-架构层面优化)
- [3. 代码质量优化](#3-代码质量优化)
- [4. 性能优化](#4-性能优化)
- [5. 测试覆盖率提升](#5-测试覆盖率提升)
- [6. 工程化改进](#6-工程化改进)
- [7. 用户体验优化](#7-用户体验优化)
- [8. 安全性增强](#8-安全性增强)
- [9. 优先级与实施路线](#9-优先级与实施路线)

---

## 1. 执行摘要

### 1.1 项目现状评估

**优势**:
- ✅ 清晰的三层架构设计（音频参考层 → 时间轴层 → 播放器层）
- ✅ 已完成大部分重构工作（阶段0-4），统一数据模型
- ✅ 良好的文档基础（architecture.md, REFACTOR_ROADMAP.md等）
- ✅ 采用现代化技术栈（Java 21, ImGui, Fabric Mod）
- ✅ 影响维度系统设计优雅（EXISTENCE/TRANSFORM/APPEARANCE/VFX）

**待改进领域**:
- ⚠️ **UI ImGui 体量**（`AudioAnalysisPanel` ~1575 行；Presenter 已有，Panel 待拆）
- ⚠️ **可度量基线**未建（JaCoCo 行覆盖、JMH 性能基准）
- ⚠️ 选区 Lasso/Brush 集成测试仍少
- ⚠️ 错误处理和用户反馈机制待完善
- ⚠️ 配置管理和扩展性有提升空间

### 1.2 关键指标

| 指标 | 当前值 | 目标值 | 优先级 |
|------|--------|--------|--------|
| 重构完成度 | ~92% | 100% | 高 |
| 单元测试 | **197** 个测试类（`./gradlew test` 通过） | 70%+ 行覆盖 | 高 |
| 代码复杂度 | Timeline/Selection 大类已拆 | 降低 20% | 中 |
| 文档完整性 | ~90% | 95% | 中 |
| 性能基准 | **未建立**（无 JMH / JaCoCo） | 建立基线 | 中 |

---

## 2. 架构层面优化

### 2.1 完成剩余重构任务（高优先级）

根据 `REFACTOR_ROADMAP.md`，以下任务需要完成：

#### 2.1.1 阶段 4.3：相机轨道与方块事件轨道对齐

**现状（2026-06 复核）**: UI 层已基本统一；剩余为验收级 polish。

| 项 | 状态 | 说明 |
|---|---|---|
| 同一 `TimelineLayout` / 播放头 | ✅ | `TimelinePanel` + `TimelineEditor` 共享标尺与 playhead |
| 相机关键帧轨渲染 | ✅ | `EventRenderer.renderCameraKeyframeRow`、`TrackRenderer` 关键帧按钮 |
| 相机↔动画行 hover 联动 | ✅ | `TimelineRowHoverHighlighter.drawActionCameraHoverHighlight` |
| 对齐辅助线 | ✅ | `InteractionState.alignmentGuideTimes` + `TimelineRenderer.drawAlignmentGuides` |
| 跨轨吸附覆盖相机关键帧 | 🟡 | 事件/片段吸附已有；相机关键帧专用吸附待确认 |
| 时间单位一致性单测 | 🟡 | `CameraKeyframe` / `TimelineAnimationEvent` 均用秒；缺显式回归测试 |

**剩余工作**（约 0.5–1 天）:
```
1. 编写 4.3 验收清单单测（时间精度、关键帧与事件同刻度）
2. 确认相机关键帧拖拽是否参与 SnapSystem
3. 通过后 REFACTOR_ROADMAP 4.3 标 ✅
```

---

#### 2.1.2 阶段 5：测试覆盖完善

**现状（2026-06 复核）**:

| 模块 | 状态 | 测试类 |
|---|---|---|
| `StepSequencePlanner` / `DistancePacing` | ✅ | `StepSequencePlannerTest`、`DistancePacingTest` |
| `BlockInfluenceEvaluator` | ✅ | `BlockInfluenceEvaluatorTest` 等 |
| `BuildSequencer` | ✅ | `BuildSequencerTest` |
| `AnimationPlayer` | ✅ | `AnimationPlayerTest` |
| `BlockControlExecutor` | ✅ | `BlockControlExecutorTest` |
| `AutoMapGenerator` | ✅ | `AutoMapGeneratorTest` |
| `OscProjectStore` | ✅ | `OscProjectStoreTest` |
| `BeatBlockSelectionManager` | 🟡 | `BeatBlockSelectionManagerTest` + `collect/*` / `tools/*`；Lasso/Brush 端到端仍少 |
| Timeline / Interaction / Rendering | ✅ | `com.beatblock.timeline.*` 大量单测（含 R1–R5 helper） |

**仍待补充**（优先级 🟡 中）:
```
1. 选区：Lasso / Brush 完整 stroke 生命周期集成测试
2. 集成：导入音频 → 分析 → 选区 → 播放 端到端（§5.2）
3. JaCoCo 行覆盖率基线（当前无数值，仅测试类计数）
```

---

### 2.2 模块解耦与职责分离

#### 2.2.1 音频处理模块解耦

**现状（2026-06 复核）**: 主干已落地；`AudioAnalysisService` 为门面。

**当前结构**:
```
AudioAnalysisService (门面)
  ├── AudioAnalysisOrchestrator ✅ (任务生命周期、并发)
  ├── IAudioAnalyzer / PythonAudioAnalyzer ✅
  ├── FfmpegService ✅ (MusicPlayer / StemMixer / AudioConversionService 共用)
  ├── PythonEnvironmentDiagnostics
  └── BeatmapAnalysisCache / AnalyzerProcessIo
```

| 项 | 状态 |
|---|---|
| `AudioAnalysisOrchestrator` | ✅ 已落地 |
| `FfmpegService` | ✅ 已落地 |
| `IAudioAnalyzer` | ✅ 已落地 |
| `JavaNativeAnalyzer` / 远程 API | ⏸ 未来扩展 |

**剩余（低优先级）**:
- `AudioAnalysisPanel` ImGui 块拆分（~1575 行，Presenter 已接入但 Panel 仍过大）
- `requirements-demucs.txt` 可选依赖说明（见 REFACTOR_ROADMAP 阶段 6）

---

#### 2.2.2 UI 与业务逻辑分离

**现状（2026-06 复核）**: Presenter 层已覆盖主要 Panel；**下一批债务在 ImGui 体量**。

| Panel / 区域 | Presenter | Panel 行数 | 状态 |
|---|---|---|---|
| `LayerPanel` | `BuildLayersPresenter` | ~190 | ✅ |
| `EventPropertiesPanel` | `EventPropertiesPresenter` | ~924 | 🟡 业务已抽，ImGui 仍大 |
| `SelectionPropertiesPanel` | `SelectionPropertiesPresenter` | — | ✅ |
| `ToolPanel` | `ToolPanelPresenter` | ~280 | ✅ |
| `TimelinePanel` / Toolbar | 多个 `Timeline*Presenter` | Toolbar ~175 | ✅ |
| `MarkerPanel` / `MenuBarPanel` | 对应 Presenter | — | ✅ |
| `AutoMapSettingsPanel` | `AutoMapSettingsPanelPresenter` | — | ✅ |
| `AudioAnalysisPanel` | `AudioAnalysisPanelPresenter` | **~1575** | 🔄 Presenter 有，Panel 待拆 ImGui 块 |
| `AnimationLibraryPanel` | — | ~32 | 占位 UI，无业务 |

**下一推荐**: 参照 `TimelineToolbar` → `Timeline*Controls` 模式，拆 `AudioAnalysisPanel` 子控件。

---

### 2.3 插件化架构设计

#### 2.3.1 动画预设插件系统

**现状**: 所有 `BlockInfluencePreset` 硬编码在 `BlockInfluencePresets` 中

**优化建议**:
```
优先级: 🟢 低（长期目标）
工作量: 大（2-3周）

设计方案:
1. 定义 Preset 配置文件格式（JSON/YAML）
   
2. 支持外部 preset 包加载
   - 从 config/beatblock/presets/ 目录扫描
   - 热重载支持（开发模式）
   
3. Preset 市场/分享机制（远期）
   - 社区用户可分享自定义动画预设
   - 版本管理与兼容性标识
```

**配置文件示例**:
```yaml
# config/beatblock/presets/custom_jump.yaml
id: "custom:mega_jump"
displayName: "超级跳跃"
defaultDuration: 0.5
channels:
  - dimension: TRANSFORM
    path: OFFSET_Y
    curve: SINE_BUMP
    from: 0.0
    to: 5.0
    durationPolicy: FULL_DURATION
```

---

#### 2.3.2 扩展点标准化

**优化建议**:
```
定义清晰的扩展接口:
1. IAnimationPresetProvider - 提供自定义动画
2. ISelectionTool - 自定义选区工具
3. IAudioFeatureExtractor - 自定义音频特征提取
4. ITimelineRenderer - 自定义轨道渲染

实施步骤:
- 第一阶段：内部重构，将现有功能改为实现标准接口
- 第二阶段：开放 API，允许外部 mod 扩展功能
```

---

### 2.4 事件溯源与状态管理优化

#### 2.4.1 命令模式增强

**现状**: `CommandManager` 实现了基础的 Undo/Redo

**优化建议**:
```
优先级: 🟡 中
工作量: 中（5-7天）

增强功能:
1. 命令合并（Merge）
   - 连续的参数微调操作自动合并为一个 Undo 单元
   - 避免 Undo 栈过于细碎
   
2. 命令宏（Macro）
   - 支持录制一系列操作为可重放的宏
   - 用于批量编辑和模板应用
   
3. 持久化 Undo 历史（可选）
   - 关闭项目后重新打开仍能撤销之前操作
   - 存储在 .osc 文件的伴随 .history 文件中
```

**代码示例**:
```java
public interface MergeableCommand extends Command {
    boolean canMergeWith(Command other);
    Command mergeWith(Command other);
}

public class UpdateEventParameterCommand implements MergeableCommand {
    @Override
    public boolean canMergeWith(Command other) {
        if (!(other instanceof UpdateEventParameterCommand)) return false;
        UpdateEventParameterCommand cmd = (UpdateEventParameterCommand) other;
        return this.eventId.equals(cmd.eventId) 
            && this.paramKey.equals(cmd.paramKey)
            && (System.currentTimeMillis() - cmd.timestamp) < 1000; // 1秒内
    }
    
    @Override
    public Command mergeWith(Command other) {
        // 保留第一个命令的 oldValue，使用最后一个命令的 newValue
        UpdateEventParameterCommand cmd = (UpdateEventParameterCommand) other;
        return new UpdateEventParameterCommand(eventId, paramKey, this.oldValue, cmd.newValue);
    }
}
```

**落地状态（2026-06）**:

| 功能 | 状态 | 说明 |
|------|------|------|
| 命令合并（Merge） | ✅ 已落地 | `MergeableCommand` + `CommandMergePolicy`（默认 1s 窗口）；`UpdateAnimationEventCommand`、`MoveEventCommand`、`ApplyClipDragCommand` 支持合并；`CommandManager.execute()` 自动合并栈顶 |
| 工程切换清空 Undo | ✅ 已落地 | `MenuBarPresenter.openProject` → `TimelineEditor.clearUndoHistory()` |
| 命令宏（Macro） | ⏸ 暂缓 | 已有 `CompositeCommand`（如 `StepSequenceBaker`）；交互式录制优先级低 |
| 持久化 Undo 历史 | ⏸ 暂缓 | `.history` 伴随文件成本高、ROI 低 |

---

### 2.5 依赖注入与可测试性

**现状（2026-06 复核）**: 第一阶段已落地；legacy 静态字段作过渡桥接。

**问题**:
- `BeatBlock.getActiveAudioPlayer()` 静态访问
- `BeatBlockSelectionManager.get()` 全局单例
- 难以进行单元测试隔离

**优化建议**:
```
优先级: 🟡 中
工作量: 大（1-2周）

重构方案:
1. 引入轻量级 DI 容器（或手动构造器注入）
   
2. 核心服务通过构造器注入依赖
   
3. 测试时可注入 mock 对象

迁移策略（渐进式）:
- 新代码强制使用 DI
- 旧代码逐步重构，优先改造核心模块
- 保留少量全局访问点作为过渡桥接
```

**重构示例**:
```java
// Before: 静态访问
public class BlockAnimationEngine {
    public void tick() {
        IAudioPlayer player = BeatBlock.getActiveAudioPlayer();
        // ...
    }
}

// After: 依赖注入
public class BlockAnimationEngine {
    private final IAudioPlayer audioPlayer;
    private final Timeline timeline;
    
    public BlockAnimationEngine(IAudioPlayer audioPlayer, Timeline timeline) {
        this.audioPlayer = audioPlayer;
        this.timeline = timeline;
    }
    
    public void tick() {
        double time = audioPlayer.getCurrentTimeSeconds();
        // ...
    }
}

// 测试时注入 mock
@Test
void testTick() {
    IAudioPlayer mockPlayer = mock(IAudioPlayer.class);
    when(mockPlayer.getCurrentTimeSeconds()).thenReturn(1.5);
    
    BlockAnimationEngine engine = new BlockAnimationEngine(mockPlayer, testTimeline);
    engine.tick();
    
    // assertions...
}
```

**落地状态（2026-06，渐进式）**:

| 阶段 | 状态 | 说明 |
|------|------|------|
| `BeatBlockContext` 运行时容器 | ✅ 已落地 | `com.beatblock.runtime.BeatBlockContext`；`BeatBlock.getContext()` 为过渡桥接；`fromLegacyStatics()` 兼容未 bind 的测试 |
| Presenter 层构造器注入 | ✅ 已落地 | `PresenterFactories` 从 Context 取依赖；支持 `setContextSourceForTests` 注入 mock |
| UI Panel 去静态化（第一批） | ✅ 已落地 | `TimelinePanel`、`EventPropertiesPanel`、`MarkerPanel`、`LayerPanel` 经 Context / Presenter 访问服务 |
| UI Panel 去静态化（第二批） | ✅ 已落地 | `AudioAnalysisPanel` + `AudioAnalysisPanelPresenter`；`AutoMapSettingsPanel` + `AutoMapSettingsPanelPresenter` |
| 引擎 / 客户端驱动去静态化 | ✅ 已落地 | `BeatBlockClientDriver`、`TimelineCameraController`、`TimelineInteraction` 已注入；`TimelineRenderer`、`AudioLoader`、`TrackRenderer`、世界渲染器已改；`TimelineEditor` 音乐播放逻辑已本地化 |
| SelectionManager / 代码生成路径 | ✅ 已落地 | `BeatBlockSelectionManager.bindContext`；`TimelineDraftWriter`、`StepSequenceBaker`、`AnimationBindingEngine`、`TimelineBuilder`、`AutoMapGenerator` 经 `BeatBlockContext` 解析引擎/命令依赖 |
| 音频资产 / 客户端生命周期 | ✅ 已落地 | `AudioAssetManager.bindContext` 解析 `externalAudioAnalyzer`；`BeatBlockClient` shutdown 经 `getContext()`；`TimelineEditor` 音乐/分段播放同步已构造器注入（无 `BeatBlock.*` 静态访问） |
| 全局 DI 容器 | ⏸ 暂缓 | 手动构造器注入已够用；暂不引入 Guice / Spring |

**新代码约定**: 业务逻辑优先通过 `BeatBlockContext` 或 Presenter 注入；Panel 避免新增 `BeatBlock.*` 静态访问。

---

## 3. 代码质量优化

### 3.1 复杂度降低

#### 3.1.1 大型类拆分

**识别的大型类（2026-06 复核）**:

| 类名 | 当前行数 | 状态 | 优化建议 |
|------|----------|------|----------|
| `BeatBlockSelectionManager` | ~720 → ~568 | ✅ Phase 3 | `SelectionToolRegistry` + `tools/*` + `collect/*` |
| `TimelineInteraction` | ~1816 → ~671 | ✅ Phase 4 | Phase 1–4 helper 拆出；主类保留 update 状态机 |
| `TimelineRenderer` | ~1897 → ~299 | ✅ R5+ | R1–R5 + `TimelineRowLabelResolver` / hover / 配对可见性 |
| `MusicPlayer` | ~787 → ~468 | ✅ 已拆 | `playback/*` backend；Clip 仍留门面 |
| `BlockAnimationEngine` | ~397 | ✅ 已门面化 | 子系统已抽出，暂无需再拆 |
| `TimelineEditor` | ~384 | ✅ 已协调层 | 交互/渲染已委托，本身不必再动 |

**下一批关注（UI 层，非 3.1.1 原表）**:

| 类名 | 行数 | 建议 |
|------|------|------|
| `AudioAnalysisPanel` | ~1575 | 拆 ImGui 子控件（Presenter 已有） |
| `EventPropertiesPanel` | ~924 | 可选：拆动画/相机编辑器块 |
| `TimelineAnimationFeatureMapper` | ~523 | R1 拆出，体量可接受 |

**TimelineRenderer 已拆 helper（R1–R5+）**:

- R1 `TimelineAnimationFeatureMapper`
- R2 `TimelineAudioDropHandler` / `TimelineAudioFeatureFillSupport` / `TimelineAudioDropHost`
- R3 `TimelineDenseFeatureApplier`
- R4 `TimelineStemMuteSync`
- R5 `TimelineRowContentRenderer`
- R5+ `TimelineRowLabelResolver` / `TimelineRowHoverHighlighter` / `TimelinePairedFeatureLaneSync` / `TimelineFeatureLaneIndex` / `TimelineAudioGroupDropHighlight`

**已拆出的选区 helper（`com.beatblock.selection`）**:

- `SelectionMerge`、`SelectionRegions`、`SelectionBrushRegions`、`BlockSelectionLine`
- `ConnectedSelectionFloodFill`、`SelectionReach`、`SelectionBounds`、`SelectionLayerBlocks`
- `PlaneSliceBounds`
- `SelectionFeedback`（合并操作反馈文案）
- `collect/ColumnSelectionCollector`（整列收集，Phase 2 POC）

**BeatBlockSelectionManager 渐进式重构**

```
优先级: 🟡 中
工作量: 增量 3–5 天（全量 Tool 化仍约 1–2 周）

Phase 1 ✅  SelectionFeedback — 合并/图层跳过反馈文案
Phase 2 ✅  selection/collect/* — BlockCollector（Box/Line/Brush/Connected/PlaneSlice/Column）
Phase 3 ✅  selection/tools/* + SelectionToolRegistry — 点击模式分发（Lasso/笔刷涂抹仍由 Manager 协调）
Phase 4 ⏸  （可选）重命名 SelectionManager，旧类作 alias

注意:
- 配置 UI 留在 ToolPanelPresenter / SelectionPropertiesPresenter，不放 Tool 接口
- Lasso / Brush 有独立生命周期（commitLasso、stampBrush、finishBrushStroke），非纯 handleClick
- Box / Line 为两步角点状态机，需 resetTransientState()
- 共享参数经 SelectionCollectContext 注入（距离、图层、maxBlocks 等）
```

**目标结构**:

```
src/main/java/com/beatblock/selection/
  ├── BeatBlockSelectionManager.java   (门面 + 选区存储 + SelectionToolHost)
  ├── SelectionFeedback.java           ✅
  ├── SelectionCollectResult.java      ✅
  ├── tools/
  │     ├── SelectionToolRegistry.java ✅
  │     ├── SelectionToolHost.java     ✅
  │     └── *SelectionTool.java        ✅ (CLICK/BOX/LINE/…)
  └── collect/
      ├── SelectionCollectSupport.java   ✅
      ├── ColumnSelectionCollector.java  ✅
      ├── BoxSelectionCollector.java     ✅
      ├── LineSelectionCollector.java    ✅
      ├── BrushSelectionCollector.java   ✅
      ├── ConnectedSelectionCollector.java ✅
      └── PlaneSliceSelectionCollector.java ✅
```

**收集器接口（Phase 2，替代文档旧版 ISelectionTool.renderConfigUI）**:

```java
public record SelectionCollectResult(List<BlockPos> blocks, String errorMessage) {
    public static SelectionCollectResult success(List<BlockPos> blocks) { ... }
    public static SelectionCollectResult failure(String message) { ... }
    public boolean failed() { return errorMessage != null; }
}

// 示例：整列收集器（已落地）
ColumnSelectionCollector.collect(world, pos, includeAir, maxBlocks, withinReach);
```

**简化后的 Manager 职责（Phase 3 目标）**:

```java
public final class BeatBlockSelectionManager {
    private final LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
    // 模式分发 → collect/* → SelectionMerge.apply → SelectionFeedback
}
```

---

#### 3.1.2 方法复杂度优化

**`BlockAnimationEngine.scheduleTimelineEvent`（2026-06 复核）**

| 项 | 原建议 | 现状 |
|---|---|---|
| 入口方法行数 | ~150 行，需 SchedulingStrategy | **~7 行**，仅分发 `ANIMATE` → `scheduleAnimateEvent` |
| 复杂逻辑位置 | 单方法 if-else | 已分散至 `scheduleExpandedStepSequence`、`scheduleFromTimelineEventWithSpatial` 等私有方法 |
| 策略模式 | 推荐立即做 | **⏸ 暂缓** — 仅当恢复 BUILD/PLACE 等多 `ActionMode` 分支时再引入 |

**结论**: 文档原示例已过时；当前优先级低于 `AudioAnalysisPanel` ImGui 拆分。若需进一步瘦身，可单独抽 `StepSequenceSchedulingSupport`（纯函数），不必上完整 Strategy 框架。

---

### 3.2 代码规范与风格统一

#### 3.2.1 命名规范完善

**当前问题**:
- 部分类名不够语义化（如 `EffectContext` vs `BlockInfluenceContext`）
- 缩写使用不一致（`Anim` vs `Animation`）
- 部分字段名过于简略

**规范建议**:
```
1. 类名规范:
   - Service结尾：提供服务的无状态/轻状态类
   - Manager结尾：管理生命周期和状态的类
   - Controller结尾：控制UI交互的类
   - Engine结尾：核心运行引擎
   - Repository/Store结尾：数据持久化
   
2. 避免缩写:
   ❌ AnimDef, AnimInst, BeatmapGen
   ✅ AnimationDefinition, AnimationInstance, BeatmapGenerator
   
   例外：常见业界缩写（FFT, BPM, UI, API等）
   
3. 布尔方法命名:
   - is/has/can 前缀：isEnabled(), hasStems(), canDelete()
   - should 前缀仅用于配置：shouldPause(), shouldCloseOnEsc()
```

---

#### 3.2.2 注释与文档规范

**当前状态**: 部分核心类缺少 JavaDoc

**改进目标**:
```
优先级: 🟡 中
工作量: 中（持续进行）

规范:
1. 所有 public 类必须有类级 JavaDoc
   - 类的职责说明
   - 使用示例（复杂类）
   - 线程安全性说明（如适用）
   
2. 所有 public 方法必须有 JavaDoc
   - @param 说明每个参数
   - @return 说明返回值
   - @throws 说明异常条件
   
3. 复杂算法添加行内注释
   - 解释"为什么"而非"做什么"
   - 引用相关文档或论文（如音频分析算法）
```

**示例**:
```java
/**
 * 管理舞台对象（StageObject）的注册、查询和生命周期。
 * 
 * <p>舞台对象是一组方块的逻辑分组，用于在时间轴上应用动画。
 * 每个对象有唯一ID、名称、方块列表和中心点。
 * 
 * <p>线程安全性：此类不是线程安全的，应在客户端主线程上使用。
 * 
 * @see StageObject
 * @see BlockAnimationEngine
 */
public final class StageObjectSystem {
    
    /**
     * 注册一个新的舞台对象。
     * 
     * @param stageObject 要注册的对象，不能为null
     * @throws IllegalArgumentException 如果ID已存在
     * @throws NullPointerException 如果 stageObject 为null
     */
    public void register(StageObject stageObject) {
        // ...
    }
}
```

---

### 3.3 错误处理改进

#### 3.3.1 异常层次结构

**当前问题**: 
- 大量使用通用异常（`Exception`, `RuntimeException`）
- 缺少领域特定异常

**优化建议**:
```
优先级: 🟡 中
工作量: 中（5-7天）

设计异常体系:
com.beatblock.core.exception/
  ├── BeatBlockException.java             (根异常)
  ├── audio/
  │   ├── AudioLoadException
  │   ├── AudioAnalysisException
  │   └── UnsupportedAudioFormatException
  ├── timeline/
  │   ├── TimelineException
  │   ├── InvalidEventException
  │   └── ProjectLoadException
  └── engine/
      ├── AnimationException
      ├── InvalidPresetException
      └── StageObjectNotFoundException
```

**代码示例**:
```java
// 根异常
public class BeatBlockException extends Exception {
    public BeatBlockException(String message) { super(message); }
    public BeatBlockException(String message, Throwable cause) { super(message, cause); }
}

// 领域特定异常
public class AudioAnalysisException extends BeatBlockException {
    private final Path audioPath;
    private final AnalysisPhase phase;
    
    public AudioAnalysisException(Path audioPath, AnalysisPhase phase, String message, Throwable cause) {
        super(String.format("音频分析失败 [%s]: %s - %s", 
            audioPath.getFileName(), phase, message), cause);
        this.audioPath = audioPath;
        this.phase = phase;
    }
    
    public Path getAudioPath() { return audioPath; }
    public AnalysisPhase getPhase() { return phase; }
}

// 使用示例
try {
    analyzer.analyze(audioPath, options);
} catch (AudioAnalysisException e) {
    LOGGER.error("分析失败", e);
    feedback.showError(String.format(
        "无法分析音频文件 %s\n阶段: %s\n原因: %s",
        e.getAudioPath().getFileName(),
        e.getPhase(),
        e.getMessage()
    ));
}
```

---

#### 3.3.2 资源清理保障

**问题识别**:
- 部分流未使用 try-with-resources
- 音频资源（Clip、OpenAL buffer）清理路径复杂

**优化建议**:
```java
// Before: 手动关闭，易遗漏
public void loadAudio(Path path) throws IOException {
    AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile());
    try {
        // process stream
    } finally {
        stream.close(); // 如果 process 抛异常，可能不执行
    }
}

// After: try-with-resources
public void loadAudio(Path path) throws IOException {
    try (AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile())) {
        // process stream
    } // 自动调用 close()，即使发生异常
}

// 复杂资源管理：使用 CleanupManager
public class AudioResourceCleanup implements AutoCloseable {
    private Clip clip;
    private Integer openAlSource;
    private Integer openAlBuffer;
    
    public void registerClip(Clip clip) { this.clip = clip; }
    public void registerOpenAlSource(int source) { this.openAlSource = source; }
    public void registerOpenAlBuffer(int buffer) { this.openAlBuffer = buffer; }
    
    @Override
    public void close() {
        if (clip != null) {
            clip.close();
        }
        if (openAlSource != null) {
            AL10.alDeleteSources(openAlSource);
        }
        if (openAlBuffer != null) {
            AL10.alDeleteBuffers(openAlBuffer);
        }
    }
}

// 使用
try (AudioResourceCleanup cleanup = new AudioResourceCleanup()) {
    Clip clip = acquireClip();
    cleanup.registerClip(clip);
    
    // ... 使用资源
    
} // 自动清理所有注册的资源
```

---

### 3.4 并发安全性

#### 3.4.1 线程安全问题识别

**状态（2026-06 复核）**: ⏸ **建议仍有效，代码未系统落地** — 无 `ThreadAssert`、`TimelineSnapshot`；`AudioAnalysisOrchestratorTest` 覆盖部分并发场景。

**潜在风险点**:
1. `Timeline` 在客户端线程读取，服务端线程可能写入（BuildSequencer）
2. `AudioAnalysisService` 多个异步任务并发
3. `SelectionManager` 状态在渲染线程和游戏tick线程访问

**优化建议**:
```
优先级: 🔴 高（安全关键）
工作量: 中（5-7天）

策略:
1. 明确线程模型文档
   - 哪些类可以跨线程访问
   - 哪些操作必须在特定线程执行
   
2. 添加线程断言
   @ClientOnly 注解 + 运行时检查
   
3. 使用不可变对象
   Timeline 数据改为 copy-on-write
   
4. 关键section加锁
   使用 ReadWriteLock 优化读多写少场景
```

**代码示例**:
```java
// 线程断言
public class ThreadAssert {
    public static void assertClientThread() {
        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException(
                "必须在客户端主线程调用，当前线程: " + Thread.currentThread().getName()
            );
        }
    }
    
    public static void assertServerThread(ServerWorld world) {
        if (!world.getServer().isOnThread()) {
            throw new IllegalStateException(
                "必须在服务端线程调用，当前线程: " + Thread.currentThread().getName()
            );
        }
    }
}

// 使用
public class BeatBlockAuthoritativeWorldMutator {
    public static void applyAuthoritative(World referenceWorld, List<BlockMutation> mutations) {
        ServerWorld serverWorld = resolveServerWorld(referenceWorld);
        ThreadAssert.assertServerThread(serverWorld); // 运行时检查
        
        // ... 安全执行世界变更
    }
}

// 不可变事件快照
public class TimelineSnapshot {
    private final List<TimelineAnimationEvent> events; // 不可变列表
    private final long snapshotTime;
    
    private TimelineSnapshot(List<TimelineAnimationEvent> events) {
        this.events = List.copyOf(events); // 防御性拷贝
        this.snapshotTime = System.currentTimeMillis();
    }
    
    public static TimelineSnapshot capture(Timeline timeline) {
        return new TimelineSnapshot(timeline.getBlockAnimationEvents());
    }
    
    public List<TimelineAnimationEvent> getEvents() {
        return events; // 已经不可变，可直接返回
    }
}
```

---

## 4. 性能优化

### 4.1 大规模方块动画优化

#### 4.1.1 渲染性能优化

**现状分析**:
- 每帧遍历所有 `AnimatedBlock` 进行矩阵变换
- 大规模场景（1000+ 方块）可能导致帧率下降

**优化建议**:
```
优先级: 🔴 高
工作量: 中（5-7天）

策略:
1. 视锥剔除（Frustum Culling）
   - 仅渲染相机可见范围内的动画方块
   - 减少不必要的矩阵计算和渲染调用
   
2. LOD（Level of Detail）
   - 远距离方块使用简化渲染
   - 超远距离只渲染粒子/光效
   
3. 批量渲染
   - 相同动画状态的方块合并渲染调用
   - 使用实例化渲染（Instanced Rendering）
   
4. 延迟更新
   - 静止/远离的方块降低更新频率
   - 仅当相机移动或方块状态变化时重新计算
```

**实现示例**:
```java
public class BeatBlockAnimatedBlocksRenderer {
    private static final double CULLING_DISTANCE = 128.0; // 剔除距离
    private static final double LOD_DISTANCE_1 = 32.0;
    private static final double LOD_DISTANCE_2 = 64.0;
    
    public void render(MatrixStack matrices, Camera camera, float tickDelta) {
        Vec3d cameraPos = camera.getPos();
        Map<BlockPos, AnimatedBlock> blocks = engine.getCurrentFrameBlocks();
        
        // 视锥剔除优化
        List<AnimatedBlock> visibleBlocks = blocks.values().stream()
            .filter(block -> isInCameraRange(block, cameraPos))
            .filter(block -> isInFrustum(block, camera))
            .collect(Collectors.toList());
        
        // LOD 分级
        for (AnimatedBlock block : visibleBlocks) {
            double distance = block.getPosition().distanceTo(cameraPos);
            int lod = determineLod(distance);
            renderBlock(block, matrices, lod, tickDelta);
        }
    }
    
    private int determineLod(double distance) {
        if (distance < LOD_DISTANCE_1) return 0; // 完整渲染
        if (distance < LOD_DISTANCE_2) return 1; // 简化
        return 2; // 最简
    }
}
```

---

#### 4.1.2 内存优化

**问题识别**:
- `AnimatedBlock` 对象可能大量创建和销毁
- 临时集合分配频繁

**优化建议**:
```
优先级: 🟡 中
工作量: 中（3-5天）

策略:
1. 对象池（Object Pool）
   - AnimatedBlock 复用，避免频繁 GC
   - 集合预分配固定容量
   
2. 数据压缩
   - 使用 packed int 存储 BlockPos（节省内存）
   - 共享不变数据（如原始位置）
   
3. 惰性计算
   - 矩阵变换结果缓存
   - 仅在数据变更时重新计算
```

---

### 4.2 音频处理性能

#### 4.2.1 FFT 优化

**现状**: `RealFFT` 实现的基础 FFT 算法

**优化建议**:
```
优先级: 🟢 低
工作量: 中（3-5天）

策略:
1. 使用优化的 FFT 库
   - JTransforms（纯 Java，已优化）
   - 或调用 native 实现（通过 JNI）
   
2. 预计算
   - 旋转因子（twiddle factors）预计算
   - 位反转索引表缓存
   
3. 并行计算
   - 大音频文件分段并行 FFT
   - 利用多核处理器
```

---

#### 4.2.2 音频解码优化

**现状**: ffmpeg 进程调用开销大

**优化建议**:
```
优先级: 🟡 中
工作量: 中（5-7天）

策略:
1. 缓存解码结果
   - 第一次转换后保存 WAV 到临时目录
   - 后续加载直接读取缓存
   
2. 流式处理
   - 避免一次性加载整个文件到内存
   - 分块读取和处理
   
3. 格式直接支持
   - 集成 MP3 decoder（如已有的 JLayer）
   - 减少对外部 ffmpeg 的依赖
```

---

### 4.3 Timeline 编辑器性能

#### 4.3.1 UI 渲染优化

**问题**: ImGui 每帧重新计算所有控件布局

**优化建议**:
```
优先级: 🟡 中
工作量: 中（3-5天）

策略:
1. 增量渲染
   - 仅重绘变更的轨道和时间范围
   - 离屏渲染不可见区域
   
2. 虚拟化长列表
   - 轨道列表虚拟滚动
   - 仅渲染可见的事件
   
3. 绘制缓存
   - 波形、频谱等静态内容缓存为纹理
   - 仅在缩放/平移时更新
```

---

#### 4.3.2 事件查询优化

**现状**: Timeline 事件存储在 List 中，线性查询

**优化建议**:
```
优先级: 🟡 中
工作量: 中（5-7天）

策略:
1. 时间索引
   - 使用 TreeMap<Double, List<Event>> 按时间索引
   - O(log n) 查询指定时间范围的事件
   
2. 空间索引（可选）
   - 对方块位置建立 R-Tree 索引
   - 快速查询影响特定区域的事件
   
3. 缓存热点查询
   - 缓存当前播放时间附近的事件
   - LRU 策略管理缓存
```

**实现示例**:
```java
public class IndexedTimeline extends Timeline {
    private TreeMap<Double, List<TimelineAnimationEvent>> timeIndex;
    private boolean indexDirty = true;
    
    @Override
    public void addBlockAnimationEvent(TimelineAnimationEvent e) {
        super.addBlockAnimationEvent(e);
        indexDirty = true;
    }
    
    public List<TimelineAnimationEvent> getEventsInRange(double startTime, double endTime) {
        rebuildIndexIfNeeded();
        
        return timeIndex.subMap(startTime, true, endTime, true)
            .values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
    
    private void rebuildIndexIfNeeded() {
        if (!indexDirty) return;
        
        timeIndex = new TreeMap<>();
        for (TimelineAnimationEvent event : getBlockAnimationEvents()) {
            timeIndex.computeIfAbsent(event.getTimeSeconds(), k -> new ArrayList<>())
                .add(event);
        }
        indexDirty = false;
    }
}
```

---

### 4.4 性能监控与分析

#### 4.4.1 性能指标收集

**优化建议**:
```
优先级: 🟡 中
工作量: 小（2-3天）

实施:
1. 添加性能计数器
   - 渲染帧率
   - 活跃动画实例数
   - 内存使用量
   - Timeline 事件数量
   
2. 性能日志
   - 长时间操作记录（>50ms）
   - 内存峰值告警
   
3. 开发者 HUD
   - 可选的性能面板
   - 实时显示关键指标
```

**实现示例**:
```java
public class PerformanceMonitor {
    private static final Map<String, Long> timings = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    
    public static <T> T measure(String operation, Supplier<T> task) {
        long start = System.nanoTime();
        try {
            return task.get();
        } finally {
            long duration = (System.nanoTime() - start) / 1_000_000; // ms
            timings.put(operation, duration);
            
            if (duration > 50) {
                LOGGER.warn("慢操作: {} 耗时 {}ms", operation, duration);
            }
        }
    }
    
    public static void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
    }
    
    public static Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timings", new HashMap<>(timings));
        metrics.put("counters", counters.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())));
        return metrics;
    }
}

// 使用
List<TimelineAnimationEvent> events = PerformanceMonitor.measure(
    "timeline.getEvents",
    () -> timeline.getBlockAnimationEvents()
);
```

---

## 5. 测试覆盖率提升

### 5.1 单元测试补充计划

#### 5.1.1 核心引擎测试

**优先级**: 🔴 高

| 模块 | 测试内容 | 预计用例数 | 状态 |
|------|----------|------------|------|
| BlockInfluenceEvaluator | 各通道求值正确性 | 15+ | ✅ `BlockInfluenceEvaluatorTest` 等 |
| StepSequencePlanner | 时间戳生成算法 | 10+ | ✅ `StepSequencePlannerTest` |
| BuildSequencer | 排序模式正确性 | 8+ | ✅ `BuildSequencerTest` |
| AnimationPlayer | 实例生命周期管理 | 6+ | ✅ `AnimationPlayerTest` |
| BlockControlExecutor | 方块变更计划 | 8+ | ✅ `BlockControlExecutorTest` |
| AutoMapGenerator | feature → 动画映射 | 8+ | ✅ `AutoMapGeneratorTest` |
| OscProjectStore | 序列化往返 | 6+ | ✅ `OscProjectStoreTest` |
| BeatBlockSelectionManager | 选区模式 | 10+ | 🟡 基础 + collect/tools 有；Lasso/Brush 集成少 |

**测试模板**:
```java
@Test
void testBuildSequencer_horizontalMode_sortsCorrectly() {
    // Arrange
    BuildLayerManager layerManager = new BuildLayerManager();
    BuildSequencer sequencer = new BuildSequencer(
        new StageObjectSystem(), 
        layerManager
    );
    
    List<BlockPos> blocks = List.of(
        new BlockPos(0, 0, 0),
        new BlockPos(1, 0, 0),
        new BlockPos(-1, 0, 0)
    );
    
    BuildLayer layer = layerManager.createFromSelection("test", blocks);
    
    TimelineAnimationEvent event = TimelineAnimationEvent.builder()
        .withParameter("layerId", layer.getId())
        .withParameter("buildMode", "HORIZONTAL")
        .build();
    
    // Act
    BuildInstance instance = sequencer.schedule(event);
    
    // Assert
    assertThat(instance.getOrderedBlocks()).containsExactly(
        new BlockPos(-1, 0, 0),
        new BlockPos(0, 0, 0),
        new BlockPos(1, 0, 0)
    );
}
```

---

#### 5.1.2 AutoMap 测试

**状态**: ✅ 已落地 — 见 `src/test/java/com/beatblock/automap/AutoMapGeneratorTest.java`。

以下为历史示例（保留作参考）:

```java
@Test
void testAutoMapGenerator_beatsToJumpAnimation() {
    // Arrange
    Timeline timeline = Timeline.createDefault();
    timeline.addFeatureEvent("beats", new FeatureEvent(1.0, 0.8f));
    timeline.addFeatureEvent("beats", new FeatureEvent(2.0, 0.9f));
    
    AutoMapConfig config = AutoMapConfig.createDefault();
    config.getRules().add(new AutoMapRule(
        "beats", 0.7f, "block_jump", 0.5, true, 2.0f
    ));
    
    // Act
    int generated = AutoMapGenerator.generate(timeline, config, false);
    
    // Assert
    assertThat(generated).isEqualTo(2);
    List<TimelineAnimationEvent> events = timeline.getAutoAnimationEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0).getTimeSeconds()).isEqualTo(1.0);
    assertThat(events.get(0).getAnimationTypeId()).isEqualTo("block_jump");
}
```

---

### 5.2 集成测试

#### 5.2.1 端到端工作流测试

**优先级**: 🟡 中

```
测试场景:
1. 完整创作流程
   - 导入音频 → 分析 → 创建选区 → 添加动画 → 播放
   
2. 项目持久化
   - 保存 .osc → 关闭 → 重新加载 → 验证一致性
   
3. 建造图层工作流
   - 创建图层 → 隐藏 → 拖入轨道 → BUILD 播放 → 验证方块出现
```

---

### 5.3 性能测试

#### 5.3.1 基准测试套件

**优先级**: 🟡 中  
**状态**: ⏸ **未集成** — 文档附录列 JMH，但 `build.gradle` 无 JMH 依赖，无 `*Benchmark*` 类。

建议首次落地时选 2–3 个热点：`BlockInfluenceEvaluator.applyPreset`、`Timeline` 事件查询、`StepSequencePlanner.plan`。

```java
@Benchmark
public void benchmarkBlockInfluenceEvaluator(Blackhole bh) {
    BlockInfluenceEvaluator evaluator = new BlockInfluenceEvaluator();
    BlockInfluencePreset preset = BlockInfluencePresets.get("block_jump");
    
    List<BlockPos> blocks = generateTestBlocks(1000);
    EffectContext context = new EffectContext(Vec3d.ZERO, Map.of());
    
    for (BlockPos pos : blocks) {
        AnimatedBlock block = new AnimatedBlock(pos);
        evaluator.applyPreset(preset, block, 0.5f, 0.8f, context);
        bh.consume(block);
    }
}

@Benchmark
public void benchmarkTimelineEventQuery(Blackhole bh) {
    Timeline timeline = generateTimelineWithEvents(10000);
    
    List<TimelineAnimationEvent> events = timeline.getEventsInRange(5.0, 10.0);
    bh.consume(events);
}
```

---

## 6. 工程化改进

### 6.1 构建系统优化

#### 6.1.1 Gradle 构建性能

**优化建议**:
```
优先级: 🟢 低
工作量: 小（1-2天）

改进:
1. 启用 Gradle 缓存
   - org.gradle.caching=true
   - 本地和远程缓存配置
   
2. 并行编译
   - org.gradle.parallel=true (已启用)
   - 增加 Xmx 到 2G（当前1G）
   
3. 增量编译优化
   - 添加 annotation processor 隔离
   - 避免不必要的重新编译
```

**gradle.properties 更新**:
```properties
# 性能优化
org.gradle.jvmargs=-Xmx2G -XX:+UseParallelGC
org.gradle.caching=true
org.gradle.parallel=true

# 配置缓存（待 IntelliJ 兼容性改善后启用）
# org.gradle.configuration-cache=true
```

---

#### 6.1.2 依赖版本管理

**现状**: `loom_version=1.15-SNAPSHOT`

**优化建议**:
```
优先级: 🟡 中

改进:
1. 锁定稳定版本
   loom_version=1.15 （正式版发布后）
   
2. 使用版本目录（Version Catalog）
   - 集中管理所有依赖版本
   - 便于跨项目共享
   
3. 依赖更新检查
   - 添加 Gradle Versions Plugin
   - 定期检查过期依赖
```

---

### 6.2 CI/CD 流程

#### 6.2.1 持续集成增强

**优先级**: 🟡 中  
**现状**: ✅ 已有 `.github/workflows/build.yml`（Ubuntu + `./gradlew build`，含测试与 natives 校验）。  
**待增强**: 多 OS 矩阵、JaCoCo 报告上传、SpotBugs 门禁（见 §6.3）。

```yaml
# .github/workflows/ci.yml（建议）
name: CI

on: [push, pull_request]

jobs:
  build-and-test:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
        java: [21]
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'
          cache: 'gradle'
      
      - name: Build with Gradle
        run: ./gradlew build
      
      - name: Run tests
        run: ./gradlew test
      
      - name: Verify bundled dependencies
        run: ./gradlew verifyBundledDependencies
      
      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports-${{ matrix.os }}
          path: build/reports/tests/
      
      - name: Upload build artifacts
        if: matrix.os == 'ubuntu-latest'
        uses: actions/upload-artifact@v4
        with:
          name: beatblock-mod
          path: build/libs/*.jar
```

---

### 6.3 代码质量工具

#### 6.3.1 静态分析

**状态（2026-06 复核）**: ⏸ **未接入** — `build.gradle` 无 Checkstyle / PMD / SpotBugs；CI（`.github/workflows/build.yml`）仅 `./gradlew build`。

**优化建议**:
```
优先级: 🟡 中
工作量: 小（2-3天）

工具集成:
1. SpotBugs
   - 检测潜在 bug
   - 空指针、资源泄漏等
   
2. Checkstyle
   - 代码风格检查
   - 统一团队编码规范
   
3. PMD
   - 代码质量检查
   - 复杂度、命名等
```

**build.gradle 配置**:
```groovy
plugins {
    id 'checkstyle'
    id 'pmd'
    id 'com.github.spotbugs' version '6.0.9'
}

checkstyle {
    toolVersion = '10.12.0'
    configFile = file('config/checkstyle/checkstyle.xml')
}

pmd {
    toolVersion = '6.55.0'
    ruleSets = []
    ruleSetFiles = files('config/pmd/ruleset.xml')
}

spotbugs {
    effort = 'max'
    reportLevel = 'medium'
}
```

---

### 6.4 文档生成

#### 6.4.1 JavaDoc 自动生成

**优化建议**:
```
优先级: 🟢 低
工作量: 小（1-2天）

配置:
1. Gradle JavaDoc 任务配置
   - 生成 HTML 文档
   - 发布到 GitHub Pages
   
2. API 文档网站
   - 使用 Docusaurus 或 MkDocs
   - 集成示例代码和教程
```

---

## 7. 用户体验优化

### 7.1 错误提示改进

#### 7.1.1 友好的错误消息

**现状**: 部分错误消息技术性过强

**优化示例**:
```java
// Before
throw new IOException("Failed to read audio file");

// After
throw new AudioLoadException(
    path,
    "无法读取音频文件",
    "可能的原因：\n" +
    "1. 文件不存在或已损坏\n" +
    "2. 不支持的音频格式（请使用 WAV/MP3）\n" +
    "3. 文件被其他程序占用\n\n" +
    "建议：使用 ffmpeg 转换为 WAV 格式后重试",
    cause
);
```

---

### 7.2 快捷键系统完善

**优化建议**:
```
优先级: 🟡 中

改进:
1. 快捷键配置化
   - 允许用户自定义快捷键
   - 保存到 config/beatblock/keybinds.json
   
2. 快捷键冲突检测
   - 启动时检查与 Minecraft 原版的冲突
   - 提示用户重新分配
   
3. 快捷键帮助
   - 内置快捷键参考面板
   - 按类别组织（编辑、播放、视图等）
```

---

### 7.3 预设管理UI

**优化建议**:
```
优先级: 🟢 低

功能:
1. Preset 浏览器
   - 可视化预览每个 preset 的效果
   - 按类别筛选（跳跃、下落、脉冲等）
   
2. 预设收藏
   - 标记常用 preset
   - 快速访问收藏列表
   
3. 自定义 Preset
   - UI 创建新 preset
   - 无需编写代码
```

---

## 8. 安全性增强

### 8.1 输入验证

**优化建议**:
```
优先级: 🔴 高（安全关键）

检查点:
1. 文件路径验证
   - 防止目录遍历攻击
   - 限制访问范围
   
2. 参数边界检查
   - 时间值：[0, maxDuration]
   - 能量值：[0.0, 1.0]
   - 方块数量：[0, maxBlocks]
   
3. JSON 反序列化
   - 防止恶意 .osc 文件
   - 限制对象图深度
```

**实现示例**:
```java
public class PathValidator {
    private static final Path ALLOWED_ROOT = Paths.get("config/beatblock");
    
    public static Path validatePath(String userProvidedPath) throws SecurityException {
        Path path = Paths.get(userProvidedPath).normalize();
        
        // 防止目录遍历
        if (!path.startsWith(ALLOWED_ROOT)) {
            throw new SecurityException(
                "拒绝访问: 路径必须在 " + ALLOWED_ROOT + " 目录下"
            );
        }
        
        // 防止符号链接逃逸
        try {
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(ALLOWED_ROOT.toRealPath())) {
                throw new SecurityException("拒绝访问: 检测到路径逃逸");
            }
        } catch (IOException e) {
            throw new SecurityException("无法验证路径", e);
        }
        
        return path;
    }
}
```

---

### 8.2 资源限制

**优化建议**:
```
优先级: 🟡 中

限制:
1. 方块选区上限
   - 默认 100,000 方块
   - 可配置，防止内存耗尽
   
2. Timeline 事件数量
   - 单轨道最多 10,000 事件
   - 超出时警告并拒绝
   
3. 音频文件大小
   - 限制 500MB（可配置）
   - 超大文件提示分割处理
```

---

## 9. 优先级与实施路线

### 9.1 短期目标（1-2个月）

#### 阶段 1: 完成重构与测试（高优先级）

**时间**: 2-3周

1. 🟡 完成阶段 4.3：相机轨道对齐（UI 已统一，验收单测待补）
2. ✅ 补充核心模块单元测试（197 测试类，`./gradlew test` 通过）
3. ⏸ 建立性能基准测试（JMH 未接入）
4. ⏸ 添加线程安全检查（`ThreadAssert` 等未实现；见 §3.4）

**验收标准**:
- JaCoCo 行覆盖率基线（目标 60%+，**当前未度量**）
- Timeline / Selection / Renderer 大类拆分完成 ✅
- 无已知的高优先级 bug

---

#### 阶段 2: 性能与稳定性（高优先级）

**时间**: 2-3周

1. 🟡 大规模方块渲染优化（持续调优，无 JMH 基线）
2. 🟡 Timeline 查询优化（子轨脏检测等已做，缺基准数据）
3. 🟡 异常层次重构（部分落地，未全面审计）
4. 🟡 资源清理审查（`TimelineRenderer.shutdown`、分析线程等已做，需定期复查）

**验收标准**:
- 1000+ 方块场景保持 60 FPS（**需实测记录**）
- 无内存泄漏
- 错误恢复机制完善

---

### 9.2 中期目标（3-6个月）

#### 阶段 3: 架构提升（中优先级）

**时间**: 4-6周

1. 🟡 模块解耦（音频 ✅；**UI ImGui 块** — `AudioAnalysisPanel` 待拆）
2. ✅ 依赖注入重构（`BeatBlockContext` + Presenter；legacy 静态桥接保留）
3. ✅ 命令模式增强（Merge + Undo 清空已落地；Macro 暂缓）
4. ⏸ 性能监控系统（JMH / 运行时指标未建）

---

#### 阶段 4: 用户体验（中优先级）

**时间**: 3-4周

1. 🔄 错误提示友好化
2. 🔄 快捷键系统
3. 🔄 Preset 浏览器 UI
4. 🔄 新手引导

---

### 9.3 长期目标（6-12个月）

#### 阶段 5: 扩展性（低优先级）

**时间**: 持续进行

1. 🔵 插件化架构
2. 🔵 外部 Preset 加载
3. 🔵 API 文档网站
4. 🔵 社区预设市场

---

### 9.4 工作量估算总结

| 优先级 | 任务类别 | 预计工作量 | 关键里程碑 |
|--------|----------|------------|------------|
| 🔴 高 | 重构完成 + 测试 | 4-6周 | 测试覆盖 60%+ |
| 🔴 高 | 性能优化 + 稳定性 | 3-4周 | 1000方块60FPS |
| 🟡 中 | 架构提升 | 6-8周 | 模块解耦完成 |
| 🟡 中 | 用户体验 | 4-5周 | 易用性提升 |
| 🟢 低 | 扩展性 | 持续 | 插件系统上线 |

**总计**: 约 4-6 个月的迭代开发

---

### 9.5 实施建议

**下一批推荐（2026-06-24）**:

1. **`AudioAnalysisPanel` ImGui 拆分** — 当前最大单体
2. **4.3 验收闭环** — 相机轨 Snap/单测，然后 REFACTOR 4.3 标 ✅
3. **JaCoCo 或 SpotBugs 接入 CI** — 让质量目标可度量
4. **文档** — 阶段 6 README / Demucs requirements 说明

---

1. **敏捷迭代**: 每 2 周一个 Sprint，每个 Sprint 聚焦 1-2 个高优先级任务

2. **持续集成**: 每次提交都通过自动化测试，保持 main 分支稳定

3. **代码审查**: 重要重构需要 code review，确保质量

4. **文档同步**: 代码变更同步更新文档，保持一致性

5. **用户反馈**: 定期收集用户反馈，调整优先级

---

## 附录

### A. 参考资料

- [BeatBlock 架构文档](architecture.md)
- [重构路线图](REFACTOR_ROADMAP.md)
- [方块影响维度](block-influence-dimensions.md)
- [STEP 三段式动画](step-phase-animation-and-cleanup.md)

### B. 工具与框架

- **测试**: JUnit 5, Mockito, AssertJ
- **性能**: JMH (Java Microbenchmark Harness)
- **静态分析**: SpotBugs, Checkstyle, PMD
- **CI/CD**: GitHub Actions
- **文档**: JavaDoc, Docusaurus

### C. 联系方式

有关优化建议的问题或讨论，请通过项目 Issue 或 PR 提出。

---

**报告结束**
