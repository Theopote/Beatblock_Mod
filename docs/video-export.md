# 视频导出

视频导出由 `VideoExportCoordinator` 在客户端主线程协调：逐帧 seek 编译快照、捕获场景、合成 VFX、写入 ffmpeg。

## 入口

```
VideoExportService.startExport(settings)
    → VideoExportCoordinator.start()
    → 每帧 scheduleNextFrame()
```

## 单帧管线

```
frameTime = VideoExportFrameClock.timelineTimeSeconds(settings, frameIndex)
    ↓
BeatBlockClientDriver.prepareExportFrameFromSnapshot(exportProgram, frameTime)
TimelineCameraController.sampleAtExportTime(frameTime)
    ↓
VideoFrameCapturer → GlobalVisualEffectFrameCompositor → FfmpegVideoEncoder
```

`exportProgram` 在导出开始时编译一次，与 live 编辑隔离。

## 帧时钟与音频

| 量 | 公式 |
|----|------|
| 时间线时刻 | `startTimeSeconds + frameIndex / fps` |
| 音频源位置 | 与上式相同（ffmpeg `-ss` 在导出起点 seek，随后与视频帧同步推进） |

示例：60fps 下第 **600** 帧 → **10.000s**；44.1kHz 下采样点 **441,000**。

## 同步回归

`VideoExportFrameSampler` 从同一编译快照采样：

- Camera — `TimelineCameraEvaluator`
- Stage — `PlaybackStateDigest.reconstructAt`
- VFX — `ExportVfxState`（与 `GlobalVisualEffectFrameCompositor` 共用）

测试：`VideoExportSyncRegressionTest`。

## 设置

`VideoExportSettings`：分辨率、fps、`startTimeSeconds` / `endTimeSeconds`、`includeAudio`。

预设：`VideoExportPresets`（YouTube / TikTok 等）。

## 依赖

- 本机 **ffmpeg**（`FfmpegService.resolveExecutable()`）
- 可选音频文件（`timeline.metadata.audioPath` 或 `MusicPlayer` 已加载路径）

## 相关代码

- `com.beatblock.client.export.VideoExportCoordinator`
- `com.beatblock.client.export.VideoExportFrameClock`
- `com.beatblock.client.export.VideoExportFrameSampler`
- `com.beatblock.audio.ffmpeg.FfmpegVideoEncoder`
