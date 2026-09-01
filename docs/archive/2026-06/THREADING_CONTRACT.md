# BeatBlock Threading Contract

本文档定义各子系统允许的执行线程，与 `Timeline` 类注释及 SpotBugs 并发告警治理一致。

**原则：** 声明在客户端主线程的代码，应通过 `ClientThreadGuard.assertClientThread()` 在 mutation 入口守住；异步结果写 UI / Timeline 结构时必须经 `ClientThreadExecutor` 派发。

## 线程矩阵

| 子系统 | 允许线程 | 说明 |
|--------|----------|------|
| Timeline 结构变更（轨道 / 片段 / 事件 / 标记） | **Client thread only** | `TimelineOperations`、`CommandManager`、受保护的 `Timeline` API |
| Timeline `metadata` 单键写入 | Client **或** worker | `ConcurrentHashMap`；worker 仅写入分析产物键（`bpm`、`beatCount` 等），禁止改轨道结构 |
| UI（ImGui / Presenter） | **Client thread only** | 由 Fabric `ClientTickEvents` / `client.execute` 驱动 |
| 播放状态（`BeatBlockClientDriver`） | **Client thread only** | `onClientTick`、`startDriving`、`stopDriving` 等 |
| 播放编译快照（`TimelineCompiler`） | Client thread | 在驱动器启动播放前于主线程编译 |
| Minecraft 世界方块写入 | **Server / 逻辑世界线程** | `BeatBlockAuthoritativeWorldMutator`、`BlockControlExecutor` |
| Python 音频分析 | **Worker** | `AudioAnalysisOrchestrator` 线程池；回调必须 `MainThreadDispatcher` → `ClientThreadExecutor` |
| ffmpeg 编码 / 转码 | **Worker** | `FfmpegVideoEncoder` 等；进度经 listener，UI 更新走主线程 |
| 文件 IO（`.osc`、缓存、beatmap） | **Worker** 或调用方线程 | 完成后若写入 Timeline 结构，须切回 client |
| 回调 → UI / Timeline 结构 | **Client dispatcher** | `ClientThreadExecutor.run` / `MainThreadDispatcher` |
| Beatmap 磁盘缓存 | **Worker-safe** | `BeatmapAnalysisCache` 等只读/写入缓存文件，不直接改 Timeline |

## 入口守卫

生产代码在以下路径调用 `ClientThreadGuard.assertClientThread()`：

- `com.beatblock.timeline.TimelineOperations` — 所有结构变更
- `com.beatblock.timeline.command.CommandManager` — `execute` / `undo` / `redo`
- `com.beatblock.timeline.Timeline` — 除 `setMetadata` 外的公开变更 API
- `com.beatblock.client.BeatBlockClientDriver` — 播放驱动与调度重置

## 异步分析 → Timeline

```
Worker: Python analyze / read beatmap
    → MainThreadDispatcher (ClientThreadExecutor)
        → AudioAnalysisEngine.fillTimeline* / SmartAutoMapEngine
            → Timeline 结构 + metadata
```

禁止在 worker 上调用 `TimelineOperations.addClip` 或 `CommandManager.execute`。

## 与 SpotBugs 的关系

- `AT_STALE_THREAD_WRITE_OF_PRIMITIVE`（如 `BeatBlockClientDriver.resetTimelineAnimationScheduling`）表示字段曾在非同步路径读写；通过 **主线程断言 + `volatile` 游标字段** 消除歧义。
- `Timeline.metadata` 的 `ConcurrentHashMap` **不**表示整棵 Timeline 线程安全，仅覆盖分析回调写入的标量元数据。

## 测试

无 `MinecraftClient` 时（JUnit），`ClientThreadGuard` 默认放行，以便无头单元测试。集成测试应通过 `ClientThreadExecutor.install` 模拟主线程队列。

## 相关代码

- `com.beatblock.client.ClientThreadGuard`
- `com.beatblock.client.export.ClientThreadExecutor`
- `com.beatblock.audio.MainThreadDispatcher`
- `com.beatblock.timeline.Timeline`（类 Javadoc）
