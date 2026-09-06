# 视频导出

视频导出由 `VideoExportCoordinator` 在客户端主线程协调：逐帧 seek 编译快照、捕获场景、合成 VFX、写入 ffmpeg。

## 入口

```
VideoExportService.startExport(settings)
    → VideoExportCoordinator.start()
    → 每帧 scheduleNextFrame()
```

对话框顶部 **Export Summary**（`VideoExportSummary`）按 Creator 版式展示：

```
Range
00:10.000 → 01:24.500
1m 14.5s

Video
1920 × 1080
60 fps
4,470 frames

Camera
Timeline Camera

Audio
song.mp3
Included

VFX
Enabled

Output
C:\...\performance.mp4
```

## 单帧管线

```
state = VideoExportFrameSampler.sample(exportProgram, settings, frameIndex, …)
    ↓
BeatBlockClientDriver.prepareExportFrameFromSnapshot(exportProgram, state.timelineTime)
TimelineCameraController.applyExportSample(state.camera)
    ↓
ExportRenderTarget (main framebuffer resized to export WxH)
    ↓
Minecraft world render @ export resolution
    ↓
ScreenshotRecorder / ExportRenderTarget.readRgbaTopDown
    ↓
GlobalVisualEffectFrameCompositor.composite(…, state.vfx, state.timelineTime)
    ↓
FfmpegVideoEncoder
```

`VideoExportFrameSampler` 是导出**语义权威**：Camera / Stage digest / VFX / Audio 对齐时刻由它一次采样；Coordinator 只应用该 `VideoExportFrameState`。舞台世界写入仍走 Driver seek（与 digest 同源时间）；勿在生产链再独立 `TimelineCameraEvaluator` / `ExportVfxState.resolve`。

`exportProgram` 在导出开始时编译一次，与 live 编辑隔离。

P2（非 blocker）：舞台世界 apply 进一步直接消费 `state.stageState()` digest，而不是仅共享 timelineTime。

非原生分辨率通过临时放大主 framebuffer（`ExportRenderTarget`）实现真分辨率渲染；导出结束时恢复窗口尺寸。若激活失败则回退到视口捕获 + 最近邻缩放。

## 帧时钟与音频

导出时间区间为**半开区间** `[startTimeSeconds, endTimeSeconds)`：

| 量 | 公式 |
|----|------|
| 区间长度 | `end - start` |
| 帧数 | `ceil(duration * fps)`；帧索引 `i ∈ [0, totalFrames)` |
| 时间线时刻 | `startTimeSeconds + frameIndex / fps`（最后一帧 < end） |
| 编码时长 | `totalFrames / fps`（与视频流长度对齐） |
| 音频 | ffmpeg `-ss start` + `-t encodedDuration`（再加 `-shortest` 兜底） |

示例：`start=0`、`end=1`、`fps=60` → 60 帧；最后一帧时刻为 `59/60 ≈ 0.983s`，**不是** `1.000s`。不要把最后一帧改成 `end`（否则会变成 61 帧）。

60fps 下第 **600** 帧 → **10.000s**；44.1kHz 下采样点 **441,000**。

## 同步回归

`VideoExportFrameSampler` 为导出语义权威（与 Coordinator 共用）：

- Camera — `TimelineCameraEvaluator` → `applyExportSample`
- Stage — `PlaybackStateDigest.reconstructAt`（世界 apply 仍由 Driver seek）
- VFX — `ExportVfxState` → `GlobalVisualEffectFrameCompositor.composite(…, vfxState, time)`

测试：`VideoExportSyncRegressionTest`。

## 设置

`VideoExportSettings`：分辨率、fps、`startTimeSeconds` / `endTimeSeconds`、`includeAudio`。

预设：`VideoExportPresets`（YouTube / TikTok 等）。

### Range UX（P2，未实现）

当前仅为 Custom：两个 `inputDouble`（start / end）。

Creator 高频路径应改为模式选择（复用已成熟的 Marker / Loop）：

| 模式 | 语义 |
|------|------|
| Entire Timeline | `[0, duration)` |
| Current Loop | `[loopIn, loopOut)`（工具栏 loop 区） |
| Between Markers | 两枚 Marker 之间（半开） |
| Custom Range | 手填起止（现状） |

尤其 **Export Current Loop** 预期会非常高频。引擎侧仍只消费 `start/end`；模式只负责填这两个数。

### 编码器 MVP（冻结）

**不要**在 Creator UI 增加：Codec / bitrate / GOP / B-frame / profile / level / pixel format / audio codec / threads。

BeatBlock 不是 Premiere。当前固定：

- Video：`libx264` · preset `medium` · **CRF 18** · `yuv420p`
- Audio：`aac` · **192k**

平台预设只选分辨率与帧率，不暴露编码旋钮。

## Export Preflight

导出前检查清单（`VideoExportPreflight`）：

| 检查项 | 阻塞？ |
|--------|--------|
| Timeline STRICT 编译 / Validator ERROR | 是 |
| Camera / StageObject / BuildLayer / VFX（含导出抬升的 WARNING） | 是 |
| Include Audio 时音频源 | 是 |
| FFmpeg | 是 |
| 时间范围 / 分辨率（含 H.264 偶数约束） | 是 |
| 输出目录可写 / 磁盘空间 | 是 |
| 输出文件碰撞 | 否（需 Replace 确认） |
| 体积估算 | 否（提示） |

UX：

```
Ready
```

或

```
Cannot Export
- Missing StageObject "tower"
- FFmpeg not found
```

门槛与导出 `CompilePolicy.STRICT` 对齐，并把会影响成片的 Stage/Camera/Layer 引用缺失抬升为阻塞。

## 覆盖策略

目标路径已存在常规文件时，UI 弹出 **Replace** 确认；未确认不得启动导出。
ffmpeg 的 `-y` 仅作用于临时编码文件，不能代替 Creator 覆盖确认。

## 原子输出

编码写入同目录唯一临时文件：`name.mp4.<random>.beatblock-export.tmp`。

| 结果 | 行为 |
|------|------|
| 成功 | `ATOMIC_MOVE`（必要时回退）→ 用户指定的 `output.mp4` |
| 取消 / 失败 | 删除临时文件；不留下半成品目标路径 |

与 `.osc` / Event Library 的 atomic-write 原则一致。

## 依赖

- 本机 **ffmpeg**（`FfmpegService.resolveExecutable()`）
- 可选音频文件（`timeline.metadata.audioPath` 或 `MusicPlayer` 已加载路径）

## 相关代码

- `com.beatblock.client.export.VideoExportCoordinator`
- `com.beatblock.client.export.VideoExportSummary`
- `com.beatblock.client.export.VideoExportPreflight`
- `com.beatblock.client.export.VideoExportAtomicOutput`
- `com.beatblock.client.export.ExportRenderTarget`
- `com.beatblock.client.export.VideoExportFrameClock`
- `com.beatblock.client.export.VideoExportFrameSampler`
- `com.beatblock.video.VideoExportAudioSource`
- `com.beatblock.audio.ffmpeg.FfmpegVideoEncoder`
