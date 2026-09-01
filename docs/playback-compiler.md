# 播放编译器（Playback Compiler）

编辑层 `Timeline` 在**正式播放**与**视频导出**前编译为不可变 `CompiledTimelineSnapshot`。播放器与导出帧采样只消费该快照。

## 数据流

```
Timeline (+ BuildLayerManager + BlockAnimationEngine)
        ↓
TimelineValidator
        ↓
TimelineCompiler  (COMPILER_VERSION = 1)
        ↓
CompiledTimelineSnapshot
        ├── stageEvents / compiledStageEvents
        ├── cameraTrack
        ├── buildLayers
        ├── markers
        ├── globalEvents
        ├── audio reference
        ├── referenceBeatTimes / bpm / duration
        └── validationReport
```

## 编译 API

```java
TimelineCompiler.compile(timeline);
TimelineCompiler.compile(timeline, engine);
TimelineCompiler.compile(timeline, engine, layerManager); // 播放推荐
```

`BeatBlockClientDriver.startDriving()` 使用三参数形式并 `playbackEngine.load(snapshot)`。

## 校验策略

`TimelineValidator` 规则示例：

| 规则 | 严重性 |
|------|--------|
| 重复 event id | ERROR |
| 未知 animation preset | ERROR |
| 事件超出时间线 | WARNING |
| BUILD 引用缺失图层 | WARNING |
| 音频文件不存在 | WARNING |

`CompilePolicy.STRICT` 遇 ERROR 失败；`SKIP_INVALID_EVENTS` 可跳过部分事件（不绕过 fatal）。

## 播放语义

- `PlaybackEngine.advance` / `seek(RECONSTRUCT_STATE)` 派发已编译事件
- `PlaybackStateDigest` 用于回归：playTo 与 reconstructAt 在探针时刻应一致
- `CompiledProgramFingerprint` 标识可执行内容哈希

## 隔离保证

- 编译后修改 live `Timeline` **不会**改变已加载快照
- 摄像机正式路径只读 `CompiledCameraTrack`，不采样 live 文档
- 视频导出通过 `prepareExportFrameFromSnapshot` 冻结同一快照

## 线程模型

| 子系统 | 线程 |
|--------|------|
| Timeline 结构变更 | Client only (`ClientThreadGuard`) |
| 编译 / 加载快照 | Client（播放开始前） |
| 世界方块写入 | Server 逻辑线程 |
| 分析 / ffmpeg | Worker → 回调切回 Client |

完整 2026-06 笔记见 [archive/2026-06/THREADING_CONTRACT.md](archive/2026-06/THREADING_CONTRACT.md)。

## 相关代码

- `com.beatblock.timeline.playback.TimelineCompiler`
- `com.beatblock.timeline.playback.PlaybackEngine`
- `com.beatblock.timeline.playback.CompiledProgramFingerprint`
- `com.beatblock.client.BeatBlockClientDriver`

## 历史阶段笔记

- [timeline-compiler-2-phase-a.md](archive/2026-06/timeline-compiler-2-phase-a.md)
- [timeline-compiler-2-phase-b.md](archive/2026-06/timeline-compiler-2-phase-b.md)
- [timeline-compiler-2-phase-c.md](archive/2026-06/timeline-compiler-2-phase-c.md)
