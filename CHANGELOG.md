# Changelog

本文件记录 BeatBlock 面向用户与集成方的**显著变更**。日常开发细节见 Git 提交历史。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### Added

- `.osc` 链式迁移框架（legacy `version` 1–4 → `schemaVersion` 3）
- 官方 Golden Project 作品级回归（`src/test/resources/projects/`）
- 视频导出帧级同步回归（`VideoExportFrameClock` / `VideoExportFrameSampler`）

### Changed

- 文档重组：核心设计文档集中于 `docs/`，历史审计材料移至 `docs/archive/2026-06/`
- 视频导出改为基于编译快照驱动舞台状态，与镜头/VFX 对齐

### Fixed

- SpotBugs baseline 机制与多项并发/空指针问题（见 `72d47ba` 等提交）

## [0.1.0] - 2026-06

### Added

- ImGui 时间轴编辑器、建造图层、方块动画引擎
- Python 音频分析、Smart Auto Map 初稿生成
- `.osc` 工程持久化、摄像机轨、全局 VFX 轨
- `TimelineCompiler` 与 `CompiledTimelineSnapshot` 正式播放路径

[Unreleased]: https://github.com/beatblock/beatblock/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/beatblock/beatblock/releases/tag/v0.1.0
