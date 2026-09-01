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

## 两套版本命名空间

| 字段 | 状态 | 说明 |
|------|------|------|
| `version` | **已冻结**（1–4） | 预 Creator Alpha 时期的 legacy 格式；仅用于读取旧工程 |
| `schemaVersion` | **长期演进** | Creator 稳定格式；当前 Creator Alpha = **3** |

**常见疑问：** 为什么 legacy `version: 4` 会迁移成 `schemaVersion: 3`？  
两者数字碰巧相同，但属于**不同命名空间**。最后一步是跨命名空间迁移（`LegacyFormatV4ToCreatorSchemaV3Migration`），写入 `format` + `schemaVersion` 并移除 legacy `version`，此后只使用 `schemaVersion`。

## 迁移链（legacy `version`，已冻结）

| 类名 | 步骤 | 变更 |
|------|------|------|
| `LegacyFormatV1ToV2Migration` | 1 → 2 | 引入 `buildLayers` |
| `LegacyFormatV2ToV3Migration` | 2 → 3 | `markers`、`animationTracks`、`durationSeconds`、`bpm` |
| `LegacyFormatV3ToV4Migration` | 3 → 4 | 可选 `choreography` |
| `LegacyFormatV4ToCreatorSchemaV3Migration` | 4 → **schema 3** | `format` + `schemaVersion`，移除 legacy `version` |

今后新增格式变更只增加 `schemaVersion` 迁移（例如 `Schema3ToSchema4Migration`），**不再**扩展 legacy `version`。

## 主要段落

| 字段 | 说明 |
|------|------|
| `animationTracks` | 动画 / 摄像机 / 全局 / feature / 建造轨 |
| `buildLayers` | 图层方块快照、可见性、绑定 clip |
| `choreography` | `ChoreographyPlan` + `AutoMapConfig` |

## 读写入口

- `com.beatblock.timeline.project.OscProjectStore`
- `com.beatblock.timeline.project.migration.OscProjectMigration`
- `com.beatblock.timeline.project.migration.OscSchemaVersions`

## 回归测试

- 单元：`OscProjectMigrationTest`、`OscProjectStoreTest`
- 作品级：`src/test/resources/projects/*.osc`（Golden Project）

## 相关文档

- [creator-workflow.md](creator-workflow.md) — 保存与备份建议
- [timeline-model.md](timeline-model.md) — 轨道与事件语义
