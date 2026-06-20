# BeatBlock

BeatBlock 是一个音乐可视化创作工具：音频分析的作用是辅助创作者快速找到节拍点、生成初稿，但最终呈现的每一个方块事件都是时间轴上的、可编辑、可重放的确定性数据，不依赖运行时重新分析音频。

## 产品定位

BeatBlock 面向的是**被观看的演出**（录屏、存档回放、他人加载工程后观看），而不是玩家实时按键的节奏游戏。

| 模式 | 核心矛盾 | 数据流 |
|------|----------|--------|
| 节奏游戏 | 输入延迟、判定窗口、实时性 | 音频播放时钟 → 立即判定（单向实时流） |
| **创作工具（BeatBlock）** | 创作者能否精确控制每一拍、每个方块何时发生什么 | 离线、可编辑、可撤销的时间轴；音频只是参考轨 |

因此：**时间轴（Timeline）是权威数据源**；音频分析只负责导入与初稿，不驱动最终呈现。

## 数据模型分层

理解代码时，不要把同名类型混为一谈。当前仓库里存在多层「事件」，职责不同：

### 1. 分析产物（只读导入层）

`audio.beatmap.Beatmap` / `BeatEvent` — Python `analyze.py` 输出的 `.beatmap` JSON 契约。

- 用途：BPM、段落、踩点、波形预览、Demucs 茎分离结果
- 生命周期：分析完成 → 读入内存 → **一次性灌入 Timeline**（`AudioAnalysisEngine.fillTimelineFromBeatmap`）
- 不是最终演出数据；不应在播放时重新分析或直接从 beatmap 驱方块

### 2. 时间轴（权威编辑层）

`Timeline` 及其上的：

- `FeatureEvent` — 特征轨上的能量点（由 beatmap 导入或手动编辑）
- `TimelineEvent` / `TimelineAnimationEvent` — 方块动画、镜头等可编排事件
- `FrequencyEvent` — **遗留**：早期 Java 内 FFT 的低/中/高频点，已被 `FeatureTrack` 取代，仅作向后兼容

播放预览时，`BeatBlockClientDriver` 按时间轴时钟把 `TimelineAnimationEvent` 交给 `BlockAnimationEngine` 执行——这是当前正确的「演出回放」路径。

### 3. 实时派发层（待淘汰）

`com.beatblock.beat.Beatmap` / `BeatEvent` + `BeatScheduler`，以及 `BeatBlockRuntime` → `AnimationScheduler` 把分析 beatmap 再转成 `beat.BeatEvent` 的路径。

- 假设：音频时钟每帧 tick → 派发节拍 → 立即触发 `AnimationManager`（早期 DisplayEntity 演示）
- 与创作工具模型冲突：事件不可编辑、不可撤销，且与 Timeline 上的 BlockAnimationEngine 路径并行
- **状态：历史遗留，不应再扩展；后续清理目标**

## 音频 → 方块的正确关系

```
音频文件
  └─ analyze.py → audio.beatmap.Beatmap（分析产物，可缓存）
        └─ fillTimelineFromBeatmap → Timeline FeatureEvent（参考轨 / 初稿）
              └─ 创作者编辑 / 自动映射规则
                    └─ TimelineAnimationEvent（确定性演出事件）
                          └─ BlockAnimationEngine（回放执行）
```

音频播放时钟只负责**对齐预览进度**，不是事件来源。

## 相关文档

- [STEP 三段式动画与参数清理](docs/step-phase-animation-and-cleanup.md)
