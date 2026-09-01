# 时间轴模型

BeatBlock 的**权威编辑层**是 `Timeline`：轨道（Track）、片段（Clip）、事件（Event）与元数据。播放器不直接读取可编辑文档。

## 轨道类型

| 轨道 ID / 类型 | 内容 |
|----------------|------|
| `audio` | 参考波形与时长（不驱动方块） |
| `animation_block` / `animation_auto` | 舞台事件（`TimelineAnimationEvent`） |
| `animation_block_feature_*` | 按频段分的 feature 轨 |
| `build_layer_*` | 建造还原 BUILD 片段 |
| `camera` | 摄像机关键帧与路径段 |
| `global` | 全局 VFX / 灯光 / 天气 |

## StageEvent（`TimelineAnimationEvent`）

核心字段：

- `timeSeconds` / `durationSeconds`
- `targetObjectId` → `RuntimeStageObject`
- `animationTypeId` → `BlockInfluencePreset`
- `parameters`：动作模式、STEP/BUILD 扩展、播放语义等

动作模式（`TimelineAnimationActionMode`）：

- `ANIMATE` — 影响维度动画（可 TRANSIENT 或 STATEFUL）
- `BUILD` — 按图层还原方块
- `PLACE` / 控制类 — 状态写入

**Preset + Target + Time = StageEvent**。无目标时可先 UNBOUND，后补绑定。

## CameraEvent

摄像机轨上的 `Clip` + `TimelineEvent`：

- `CAMERA_KEYFRAME` — 位置、yaw/pitch
- `CAMERA_SEGMENT` — `CameraSegmentKind`（PATH / DOLLY / ORBIT / CRANE / SHAKE）

编译后进入 `CompiledCameraTrack`（见 [playback-compiler.md](playback-compiler.md)）。

## GlobalEvent

全局轨事件，编译为 `CompiledGlobalEvent` + 强类型 `GlobalEventPayload`：

- 环境光、屏幕色调、闪屏、天气、粒子等
- 部分为 STATEFUL（导出帧合成时持续生效）

## 方块影响维度

三种演出共用同一求值器（`BlockInfluenceEvaluator`）：

| 维度 | 含义 |
|------|------|
| EXISTENCE | 方块出现/消失 |
| TRANSFORM | 位移、旋转、缩放 |
| APPEARANCE | 颜色、材质脉冲 |
| VFX | 粒子等独立效果 |

历史细节见 [archive/2026-06/block-influence-dimensions.md](archive/2026-06/block-influence-dimensions.md)。

## STEP 序列

- **推荐**：工具栏「烘焙 STEP」→ 展开为多个带绝对时间的 BURST 事件
- **过渡**：保留 `dispatchModel=STEP` 时由 `BlockAnimationEngine` 首次调度时展开

规划器：`StepSequencePlanner`、`PacingStrategy`、`DistancePacing`。

## 持久化

`.osc` 中 `animationTracks` 保存轨道 JSON；详见 [project-format.md](project-format.md)。

## 相关代码

- `com.beatblock.timeline.Timeline`
- `com.beatblock.timeline.TimelineOperations`
- `com.beatblock.timeline.project.TimelineAnimationPersistence`
