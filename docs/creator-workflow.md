# 创作者工作流

本文描述 BeatBlock 从空白世界到可导出演出的典型路径。技术分层见 [architecture.md](architecture.md)。

## 1. 准备舞台

1. 进入世界，用选区工具框选方块
2. 将选区保存为 **建造图层（Build Layer）** 或注册为 **舞台对象（Stage Object）**
3. 需要「先藏后显」的图层设为隐藏，播放 BUILD 时再还原

## 2. 绑定音频

1. **文件 → 导入音频**（WAV 优先；其他格式经 ffmpeg 转换）
2. 运行 Python 分析（BPM、节拍、段落、频段能量）
3. 分析结果写入**第 1 层参考轨**，可手改，不进入播放器

## 3. 编排时间轴（第 2 层）

| 轨道 | 用途 |
|------|------|
| 动画 / Auto | 方块动作、跑酷敲击、脉冲等 |
| 建造还原 | BUILD 片段，绑定建造图层 |
| 摄像机 | 关键帧、路径段、自动镜头（Smart Auto Map 可生成初稿） |
| 全局 | 灯光、闪屏、色调、天气等 VFX |
| 标记 | 段落、Drop 等创作参考 |

操作习惯：

- 从 Animation Library 拖拽 preset → **Preset + Target + Time**
- 用吸附对齐节拍；Smart Auto Map 生成可编辑初稿，非最终演出
- 所有编辑支持 Undo/Redo（`CommandManager`）

## 4. 预览与校验

1. 拖动时间线或按播放预览
2. 正式播放走 **编译快照**（`TimelineCompiler`），与 live 文档隔离
3. 菜单中的性能/校验会报告缺失 preset、未绑定目标等问题

## 5. 保存与迁移

- **Ctrl+S** 保存 `.osc` 工程（见 [project-format.md](project-format.md)）
- 旧版 `version` 工程加载时自动链式迁移到 `schemaVersion: 3`

## 6. 导出视频

1. **文件 → 导出视频**，选择分辨率、帧率、时间范围
2. 导出逐帧 seek 编译快照，合成镜头与屏幕 VFX（见 [video-export.md](video-export.md)）
3. 可选混入音频；起点与片段时间对齐

## 三种典型演出（同一抽象）

| 演出 | 编排要点 |
|------|----------|
| 建筑从无到有 | BUILD / STEP + 固定或慢速环绕镜头 |
| 跑酷敲击 | 短脉冲 BURST、跟随镜头 |
| 镜头跟随下落 | 长轨迹 TRANSFORM + 主动相机路径 |

参数与维度细节见 [timeline-model.md](timeline-model.md) 与 [architecture.md](architecture.md)。

## 相关文档

- [timeline-model.md](timeline-model.md) — 时间轴与事件模型
- [automap.md](automap.md) — Smart Auto Map
- [audio-analysis.md](audio-analysis.md) — 分析管线
- [camera.md](camera.md) — 镜头系统
