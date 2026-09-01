# 音频分析

第 1 层**音频参考轨**为创作对齐服务，不进入播放器运行时路径。

## 管线

```
音频文件 (WAV / ffmpeg 转换)
    ↓
Python analyze.py  →  Beatmap (version 1 JSON 缓存)
    ↓
AudioAnalysisEngine.fillTimelineFromBeatmap
    ↓
Timeline 参考数据：BPM、节拍点、段落、频段能量、波形
```

## Beatmap 契约

- 类型：`com.beatblock.audio.beatmap.Beatmap`
- 磁盘缓存：`BeatmapAnalysisCache`
- 播放器**禁止**读取 beatmap 或运行时重新分析

## 分析内容

| 产物 | 用途 |
|------|------|
| BPM / beat times | 吸附、标记、Auto Map |
| Sections | 段落色带、编舞计划 |
| Stem / 频段能量 | Feature 轨、三频段动画 |
| Waveform | 时间线波形显示 |

## Python 环境

- 脚本：`src/main/resources/beatblock/analyzer/analyze.py`
- 依赖见同目录清单；Demucs 分轨可选
- 在 Worker 线程执行；结果经 `MainThreadDispatcher` 写回 Timeline

## 播放时钟

`MusicPlayer` / `StemMixer` 仅驱动**预览进度**，不是事件来源。导出时音频 seek 与视频帧时钟对齐（见 [video-export.md](video-export.md)）。

## 相关代码

- `com.beatblock.audio.analysis.AudioAnalysisEngine`
- `com.beatblock.audio.analysis.AudioAnalysisOrchestrator`
- `com.beatblock.audio.beatmap.BeatmapReader`
