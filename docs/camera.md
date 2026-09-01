# 摄像机系统

摄像机在第 2 层编排，在第 3 层由 `TimelineCameraController` 采样并写入 `CameraRuntime`。

## 组件

| 组件 | 职责 |
|------|------|
| `TimelineCameraEvaluator` | 按时间采样位置、yaw、pitch |
| `TimelineCameraController` | 播放/拖动/导出时接管相机 |
| `CameraRuntime` | 应用 sample、与玩家相机插值 |
| `CameraTrackFactory` | 路径段、关键帧编译辅助 |

## 采样路径

- **正式播放**：只读 `CompiledCameraTrack`（编译快照）
- **Scrub / 关键帧预览**：可读 live `Timeline`
- **视频导出**：`sampleAtExportTime` + 冻结的 `exportProgram`

## CameraShot（自动映射）

语义化镜头模型（Smart Auto Map）：

- `subject` — 拍谁（StageObject / BuildLayer / StageGroup 等）
- `framing` / `movement` / `transition`
- 编译为 `CAMERA_SEGMENT` 轨事件

## 段类型（`CameraSegmentKind`）

`PATH` · `DOLLY` · `ORBIT` · `CRANE` · `SHAKE`

## 相关代码

- `com.beatblock.client.camera.*`
- `com.beatblock.timeline.camera.CameraTrackFactory`
- `com.beatblock.automap.camera.CameraShot`
