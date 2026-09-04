# Creator Alpha Showcase

作品级审美回归夹具，不是单元测试替代品。

**工程文件：** [`src/test/resources/projects/creator-alpha-showcase.osc`](../src/test/resources/projects/creator-alpha-showcase.osc)  
**程序化定义：** `GoldenProjectFixtures.creatorAlphaShowcase()`

> 单元测试绿 ≠ Showcase 通过。结构回归只证明「能加载 / 能编译 / 关键内容在」；好看不好看以本页人工评分为准。

## 规格

| 项 | 值 |
|----|----|
| 时长 | 80s |
| BPM | 128 |
| StageObjects | Tower_A … Tower_H（8） |
| Sections | Intro → Verse → Pre-Chorus → Chorus → Build → Drop → Break → Final |

强制包含：Accent / Phrase / Hero、≥4 Spatial Pattern（CASCADE / WAVE / ALTERNATE / EXPLODE）、MANUAL 事件、Camera、VFX/Particle、真实 `beatTimes`。

## 打开与导出

1. 将 `creator-alpha-showcase.osc` 复制到本机工程目录，或从测试资源导出后用 Creator 打开  
2. 确认 8 个 Tower 已绑定；工程内 `audioPath` 可为占位（`golden://…`）。打开后请换成真实 80s 曲目再听审 / Export  
3. 播放全曲；重点看 Chorus / Drop / Final  
4. 对 Chorus 做一次 Section Recompile，确认其它段与 MANUAL 仍在  
5. Export：场景画面 +（可选）音频，抽查镜头切换是否生硬

## 强制检查清单

- [ ] Accent（pulse 纹理）在 Verse / Break 等段落可见且不抢戏  
- [ ] Phrase 至少出现 CASCADE / WAVE / ALTERNATE / EXPLODE  
- [ ] Hero 在 Drop 与 Final 有清晰记忆点  
- [ ] MANUAL 编辑痕迹在重编译后仍保留  
- [ ] Chorus Section Recompile 不误删 Drop / Final  
- [ ] Camera 镜位变化可读；硬切 / 平滑不穿帮  
- [ ] VFX / Particle 在高潮有点缀  
- [ ] Export 成片可审片

## 人工评分表

| 指标 | 目标 | 本次 |
|------|------|------|
| 节奏准确 | ≥ 9/10 | /10 |
| 空间可读性 | ≥ 8/10 | /10 |
| 编舞层次 | ≥ 8/10 | /10 |
| 留白 | ≥ 7/10 | /10 |
| 高潮记忆点 | ≥ 8/10 | /10 |
| 重复控制 | ≥ 8/10 | /10 |
| 镜头舒适度 | ≥ 8/10 | /10 |

**通过条件：** 全部指标达到目标分；任一项不达标则记为 Showcase Fail，并在笔记写明原因。

## 评分记录模板

```
日期:
构建 SHA:
评分人:
节奏准确:
空间可读性:
编舞层次:
留白:
高潮记忆点:
重复控制:
镜头舒适度:
结论: PASS / FAIL
观感笔记:
```

## Spatial Pattern 冻结（Alpha）

Creator Alpha **冻结**现有 [`SpatialMotifId`](../src/main/java/com/beatblock/automap/choreography/SpatialMotifId.java) 集合，不再为「数量」新增 pattern。

下一阶段只做编舞决策质量：何时用 / 用多久 / 用多强 / 与前一个如何连接 / 何时什么都不动 / HeroScore 稀缺性。

## 自动回归（结构）

- `CreatorAlphaShowcaseRegressionTest` — 结构断言 + Chorus section recompile  
- `GoldenProjectRegressionTest.CREATOR_ALPHA_SHOWCASE` — load → compile → seek → save/reload fingerprint  

重新生成 `.osc`：

```bash
./gradlew test --tests com.beatblock.timeline.project.golden.GoldenProjectResourceGeneratorTest
```
