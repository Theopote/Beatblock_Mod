# 贡献指南

感谢关注 BeatBlock。本项目是 Minecraft Fabric 模组，核心约束是**时间轴为权威数据源、播放器只消费编译快照**。

## 开发环境

见 [README.md](README.md) 中的环境要求与构建说明。需要：

- JDK 21+
- Gradle（随仓库 wrapper）
- Minecraft 1.21.11 + Fabric Loom
- （可选）Python 3.10–3.12、ffmpeg，用于音频分析与视频导出

## 工作流

1. Fork 仓库并基于 `master` 创建分支
2. 小步提交，提交信息建议：`type(scope): 简述`（如 `feat(timeline): …`、`fix(export): …`）
3. 运行测试：`./gradlew test`
4. 若改动影响 SpotBugs 基线，需有理由并尽量**减少** baseline 计数，不要随意扩大
5. 发起 Pull Request，说明动机、测试方式与已知限制

## 架构约束（提交前自检）

- 新功能应能归入 [docs/architecture.md](docs/architecture.md) 三层模型中的某一层
- 播放器路径不得读取 beatmap 或运行时重新分析音频
- Timeline 结构变更须在客户端主线程（见 `ClientThreadGuard`）
- `.osc` 格式变更须提供 `ProjectMigration` 链式步骤与测试

## 测试期望

- 单元测试：`./gradlew test`
- 作品级回归：Golden Project（`com.beatblock.timeline.project.golden.*`）
- 导出同步：`com.beatblock.client.export.VideoExportSyncRegressionTest`

## 文档

- 用户向说明更新 [README.md](README.md)
- 设计变更更新 `docs/` 下对应主题文档，勿在根目录堆积一次性审计报告
- 历史材料归档至 `docs/archive/YYYY-MM/`

## 行为准则

保持讨论对事不对人。欢迎问题与改进建议；破坏性 API 变更请在 PR 中明确标注。
