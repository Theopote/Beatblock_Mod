# 工程格式（.osc）

`.osc` 是 JSON 轻量工程文件，保存时间线、图层、编舞与元数据。

## 当前稳定 Schema

```json
{
  "format": "beatblock.osc",
  "schemaVersion": 3,
  "projectId": "...",
  "projectPath": "...",
  "timelineName": "...",
  "audioPath": "...",
  "durationSeconds": 60.0,
  "bpm": 120.0,
  "markers": [],
  "buildLayers": [],
  "buildLayerGroups": [],
  "animationTracks": [],
  "choreography": { }
}
```

- **新保存**只写 `schemaVersion`，不再写 legacy `version`
- **加载**时由 `OscProjectMigration` 链式升级旧工程

## 迁移链

| 步骤 | 变更 |
|------|------|
| v1 → v2 | 引入 `buildLayers` |
| v2 → v3 | `markers`、`animationTracks`、`durationSeconds`、`bpm` |
| v3 → v4 | 可选 `choreography` |
| v4 → schema 3 | `format` + `schemaVersion`，移除 legacy `version` |

## 主要段落

| 字段 | 说明 |
|------|------|
| `animationTracks` | 动画 / 摄像机 / 全局 / feature / 建造轨 |
| `buildLayers` | 图层方块快照、可见性、绑定 clip |
| `choreography` | `ChoreographyPlan` + `AutoMapConfig` |

## 读写入口

- `com.beatblock.timeline.project.OscProjectStore`
- `com.beatblock.timeline.project.migration.OscProjectMigration`

## 回归测试

- 单元：`OscProjectMigrationTest`、`OscProjectStoreTest`
- 作品级：`src/test/resources/projects/*.osc`（Golden Project）

## 相关文档

- [creator-workflow.md](creator-workflow.md) — 保存与备份建议
- [timeline-model.md](timeline-model.md) — 轨道与事件语义
