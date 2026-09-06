# 摄像机系统

摄像机在第 2 层编排，在第 3 层由 `TimelineCameraController` 采样并写入 `CameraRuntime`。

## 组件

| 组件 | 职责 |
|------|------|
| `TimelineCameraEvaluator` | 按时间采样位置、yaw、pitch |
| `TimelineCameraController` | 播放/拖动/导出时接管相机 |
| `CameraRuntime` | 应用 sample、与玩家相机插值 |
| `CameraTrackFactory` | 路径段、关键帧编译辅助（低层；UI 不直调） |
| `CameraShot` / `CameraShotDraft` | 创建意图（语义草稿） |
| `CameraShotTimelineWriter` | 将草稿单向编译为 Timeline Camera Clip |
| `CameraShotInsertionService` | 手工插入事务：Command / Undo / 选中 / notify |

## 采样路径

- **正式播放**：只读 `CompiledCameraTrack`（编译快照）
- **Scrub / 关键帧预览**：可读 live `Timeline`
- **视频导出**：`sampleAtExportTime` + 冻结的 `exportProgram`

## CameraShot = 创建意图（单向编译）

`CameraShot` / `CameraShotDraft` 是 **creation intent / semantic draft**，不是长期与 Timeline 双向同步的第二真相源。

```
Manual Camera Creator / Context menu Add Segment
        ↓
CameraShot / CameraShotDraft
        ↓
CameraShotTimelineWriter
        ↓
Timeline Camera Clip   ← 之后的 source of truth

AutoMap CameraDirector
        ↓
CameraShot
        ↓
CameraShotTimelineWriter
        ↓
Timeline
```

- **语义草稿**（Creator Panel / AutoMap）：`subject` · `framing` · `movement` · timing → framing 解算眼点后写入。
- **姿态草稿**（Context menu）：live eye / yaw / pitch（或 orbit 拟合）挂在 `CameraShotDraft.pose`，仍经 Writer 编译，保留当前朝向几何。
- **编译一次之后**：用户直接编辑 Timeline 的 keyframe / segment / duration / transform。**不要**强制 `CameraShot` ↔ Timeline 实时双向同步。
- **Kind 切换**（Properties combo）：`CameraSegmentParamSchema.remintForKind` — 保留 shared 语义/provenance，清除旧 kind 几何字段，补齐新 kind defaults（避免 Orbit→Crane 残留 `radius` / `yawStartDeg`）。

## Camera Creator（语义优先）

Camera Creator 的主路径是：

- **Subject** — 拍谁（StageObject / All）
- **Framing** — 怎么构图（Wide / Medium / Close / Overview）
- **Movement** — 怎么运动（Orbit / Push In / …）

顶部 **Visualization** 工具栏（创作可视化，不是单 clip 属性）：

- Show Camera Path — 全局开关世界轨迹（仍尊重 per-clip Properties「显示路径」）
- Show Frustum — 播放头机位视锥
- Show Subject Bounds — 当前 Creator Subject 包围盒

`CameraFramingEngine` 根据主体 `StageBounds` 计算 distance / look-at / pitch；**不要**把手工填 x/y/z 当作主工作流。

创建成功后：自动选中新 Camera Clip + segment → 打开 Timeline Properties（与 Event Library 自动选中原则一致）。

`Capture Current View` 是姿态优先旁路（PATH 关键帧）；坐标精调只在 Properties。

视角角（Front / 3/4）与节拍时长（N beats）为后续增强，当前时长仍为秒。


## 相关代码

- `com.beatblock.client.camera.*`
- `com.beatblock.timeline.camera.CameraTrackFactory`
- `com.beatblock.automap.camera.CameraShot`
- `com.beatblock.automap.camera.CameraShotDraft`
- `com.beatblock.automap.camera.CameraShotTimelineWriter`
- `com.beatblock.automap.camera.CameraShotInsertionService`
