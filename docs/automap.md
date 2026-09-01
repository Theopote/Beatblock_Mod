# Smart Auto Map

Smart Auto Map 在**编辑时**根据音乐结构与舞台资源生成时间轴**初稿**，经 `TimelineDraftWriter` 写入并可 Undo。属于创作辅助，不是播放路径。

## 流程

```
Beatmap + StageObject / BuildLayer
        ↓
ChoreographyPlan（段落、角色分配、动作短语、镜头短语）
        ↓
SmartAutoMapEngine / AnimationBindingEngine
        ↓
Timeline 动画轨 / 摄像机轨 / 全局事件初稿
```

## 核心类型

| 类型 | 作用 |
|------|------|
| `ChoreographyPlan` | 段落计划、动作/镜头短语、密度曲线 |
| `AutoMapConfig` | 特征 → 舞台目标映射 |
| `AutoMapRule` | 频段阈值、动画类型、强度 |
| `CameraShot` | 带主体的镜头（subject / framing / movement） |
| `CameraDirector` | 由段落与舞台对象生成 `CameraShot` 列表 |

## 编舞持久化

`ChoreographyPlanPersistence` 将计划写入 `.osc` 的 `choreography` 段（见 [project-format.md](project-format.md)）。

## 与播放器边界

- 自动映射只在用户触发或导入时运行一次
- 生成结果为普通 `TimelineAnimationEvent` / 摄像机事件
- 正式播放仅编译第 2 层已有事件

## 相关代码

- `com.beatblock.automap.SmartAutoMapEngine`
- `com.beatblock.automap.choreography.*`
- `com.beatblock.automap.camera.CameraDirector`
- `com.beatblock.timeline.generation.TimelineDraftWriter`
