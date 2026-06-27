# BeatBlock 合并指南：场景一/三改动 + 一处图层 bug 修复

> 给 Cursor 看的上下文文档。目的：让它在合并下面这批文件时，理解**为什么**这么改，
> 不要在合并过程中"顺手优化"掉看起来奇怪、但其实是有意为之的设计决定。

## 0. 项目背景（一句话）

BeatBlock 是 Minecraft Fabric Mod，定位是**音乐可视化创作工具**（录屏/演出用，不是实时玩家操作的节奏游戏）。核心架构是三层单向数据流：

```
第 1 层 音频参考轨（只读）  →  第 2 层 Timeline 事件（权威编辑层）  →  第 3 层 播放器（只消费第 2 层）
```

第 3 层播放器内部又分两条路：
- **ANIMATE**：纯视觉效果（移动/旋转/缩放/外观），不碰真实世界方块，走 `BeatBlockAnimatedBlocksRenderer` 渲染层。
- **BUILD / PLACE / CLEAR**：需要真正持久化的世界写入（建造揭示、图层揭示），通过 `WorldMutationSink` 注入的权威 sink（`BeatBlockAuthoritativeWorldMutator`）调度到服务端线程执行，不能直接改客户端世界。

## 1. 这次改动之前，代码库里已经有什么（不需要 Cursor 处理，仅作背景）

以下两套机制**已经在当前代码库里**，确认完整、不需要任何合并动作，只是 Cursor 理解后面改动时需要知道它们存在：

1. **`WorldMutationSink` / `BeatBlockAuthoritativeWorldMutator`**（`engine/WorldMutationSink.java`、`client/BeatBlockAuthoritativeWorldMutator.java`）：解决"BUILD/PLACE/CLEAR 直接改客户端世界，不会持久化、不会同步给其他玩家"的问题。单人模式下解析成对应维度的 `ServerWorld`，通过 `MinecraftServer.execute(...)` 调度到服务端线程写入。联机模式（无本地集成服务端）目前不支持权威写入，只打一次警告日志并跳过，**故意不回退到客户端假写**。
2. **`engine.layer` 包下的完整图层系统**（`BuildLayer`/`BuildLayerManager`/`BuildLayerPersistence`/`BlockStateCodec` + `timeline/command/layer/*` 命令 + `ui/panels/LayerPanel.java`）：对应"选中真实建筑方块→放进图层→隐藏→拖到 BUILD 反向轨道→播放头驱动逐块按各自原始材质揭示"的完整工作流，含 Undo/Redo、项目存档持久化、方块归属冲突检测。`BuildSequencer` 通过持有 `BuildLayerManager` 依赖、在 `schedule()` 时实时读 `BuildLayer.getCapturedStates()`，避免了"捕获快照与已注册 `StageObject` 不同步"的潜在问题。

## 2. 这次真正要合并的改动

合并包目录结构（`com/beatblock/...` 对应你工程里 `src/main/java/com/beatblock/...`）：

```
com/beatblock/timeline/command/layer/DeleteLayerCommand.java        [修改]
com/beatblock/engine/AnimatedBlock.java                              [修改]
com/beatblock/client/render/BeatBlockAnimatedBlocksRenderer.java     [修改]
com/beatblock/engine/influence/AppearancePulseTracker.java           [修改]
com/beatblock/engine/influence/BlockInfluenceOrchestrator.java       [修改]
com/beatblock/engine/influence/PathKind.java                         [修改]
com/beatblock/engine/influence/BlockInfluencePresets.java            [修改]
com/beatblock/engine/influence/ImpactVfxTracker.java                 [新增]
com/beatblock/timeline/generation/RhythmDropEventFactory.java        [新增]
```

直接用合并包里的文件**覆盖**对应路径即可（都是基于你最新代码库改的，不是从旧版本打的补丁）。

---

### 2.1 bug 修复：`DeleteLayerCommand` 撤销删除隐藏图层时方块没有重新隐藏

**现象**：撤销一次"删除一个处于隐藏状态的图层"操作后，图层恢复了，但对应的真实方块变成可见状态，而不是回到删除前的隐藏状态。

**根因**：`BuildLayerManager.deleteLayer()` 对 `FREE_HIDDEN` 图层会先调用 `showLayer()` 把方块恢复可见（这是对的——删除前清理干净），但这个调用会把图层对象的 `state` 字段**就地改写**成 `FREE_VISIBLE`。原来的 `undo()` 只是把这个（状态已经被改写过的）对象塞回管理器，从未让它重新隐藏。

**修复**：`execute()` 一开始就单独记一份 `previousState`（在 `deleteLayer()` 改写它之前），`undo()` 时如果记录的是 `FREE_HIDDEN`，在 `registerRestored(...)` 之后再调一次 `manager.hideLayer(...)`，让它按当前世界状态（此时应该正好和删除前一致）重新捕获并隐藏。

无其他依赖变化，签名不变（`execute()`/`undo()` 都是无参），可以直接覆盖。

---

### 2.2 场景一修复："跑酷踩点瞬间变色"不应该走真实世界写入

**问题背景**：原来的 `AppearancePulseTracker`（APPEARANCE 通道，对应踩点闪烁这类效果）实现方式是：动画过中点时调用 `frame.addWorldMutation(...)` 真实改方块材质，动画结束时再写一次 mutation 还原。这条路径最终会经过 `BeatBlockAuthoritativeWorldMutator`，所以"持久化"这件事本身没问题，但**性能模型错了**：跑酷踩点的本质是高频、密集触发（一段快节奏演出可能每秒好几次），每次触发都是一次真实 `setBlockState`，都会带来区块网格重建的开销——这正是"别让真方块参与高频动画"这条原则本该规避的问题，只是换了个触发路径（PLACE/CLEAR 而不是直接的方块移动）又把它带回来了。

**修复方向**：闪烁改成纯渲染层效果，完全不碰真实世界数据。

**改动明细**：

- **`AnimatedBlock.java`**：新增 `appearanceOverride`（可选 `BlockState` 字段）。非空时渲染器应该画这个状态，而不是该坐标真实世界方块的状态——原始方块本身完全没被改动。`resetToOriginal()` 里同步把它清空，保证每帧从空白状态重新叠加。

- **`BeatBlockAnimatedBlocksRenderer.java`**：渲染时 `state = override != null ? override : world.getBlockState(orig)`，优先用覆盖值。同时修了 `isRedundantWithWorldBlock`——原来的逃生判断是"位置/旋转/缩放都没变就跳过绘制（避免 z-fighting 发黑）"，但如果只是外观覆盖、方块本身没有任何位移，这个判断会让闪烁效果完全不渲染。现在加了一条：**只要有外观覆盖就必须绘制**，不再因为静止就跳过。

- **`AppearancePulseTracker.java`**：整个重写。不再写 `BlockMutation`，改成在对应 `AnimatedBlock` 上调用 `setAppearanceOverride(flash)`。触发语义也从"只闪一次"改成"过了触发点（默认 t≥0.5）之后，每帧持续设置覆盖，直到动画实例结束"——因为每帧 `AnimatedBlock` 都会被 `resetToOriginal()` 清空重建，必须每帧重新设置才能让闪烁在整个剩余时长里保持可见，不是闪一下就消失。动画实例结束后，`AnimationPlayer` 自然不再为这个位置创建 `AnimatedBlock`，闪烁也就自动消失，不需要任何"还原"逻辑。

- **`BlockInfluenceOrchestrator.java`**：
  - 配合上面的简化，删掉了一段现在完全重复的清理循环（原来分别有一个"专门处理刚结束实例、调用 `appearanceTracker.revert(frame, world)`"的循环，和另一个"处理所有不再活跃实例、调用 `clearInstance`"的循环——两者实际清理的是同一件事，`revert` 简化后和 `clearInstance` 做的事完全一样，于是删掉了前一个、只保留后一个）。
  - 新增 `ImpactVfxTracker` 字段并接入调用链（见下面场景三部分）。

**如果你的代码库里这几个文件和这次提供的版本有冲突**（比如你们自己又顺手改过 `AppearancePulseTracker` 之类的逻辑），优先保留这次的"只设置渲染层覆盖、不写真实世界"这条核心原则，具体实现细节可以按你们自己的风格调整。

---

### 2.3 场景三新功能："天降方块踩节奏命中、闪粒子、消失"——节奏大师式效果

这个效果全程不碰真实世界方块：下落中的视觉方块走 ANIMATE 渲染层，它"落在"的目标方块本身是已存在的真实方块，命中时只在那个坐标触发一次粒子，不改变那个真方块的任何状态。命中后视觉方块自然消失（位置归零、与世界方块重合后被 `isRedundantWithWorldBlock` 判定为冗余，渲染器自动停止绘制），不需要额外的"消失"逻辑。

**新增/修改明细**：

- **`PathKind.java`**：新增 `IMPACT_TRIGGER` 枚举值，对应 VFX 维度"接近结束触发一次"的语义，和已有的 `BLOCK_STATE`（对应 APPEARANCE 维度）、`VISIBLE`（对应 EXISTENCE 维度）是同一种命名惯例。

- **`BlockInfluencePresets.java`**：新增内置预设 `"RhythmDrop"`（节奏坠落）。用 `WORLD_TRAJECTORY` + `GRAVITY_REMAINING` 曲线做直线下落（和已有的 `Meteor` 预设是同一条几何路径，只是默认高度从 12 降到 6，更贴近"踩点命中"而不是"流星砸落"的视觉感觉），外加一个 `VFX` 通道（`IMPACT_TRIGGER`，阈值 `0.92`）。

- **`ImpactVfxTracker.java`**（新文件）：和 `AppearancePulseTracker` 同一种设计模式的姐妹类——每帧检查动画实例是否过了触发阈值，过了就对目标方块各派发一次 `VfxTrigger`，只触发一次（用 `fired` 集合去重）。比 `AppearancePulseTracker` 更简单，因为 VFX 本身就是瞬时事件，不需要"持续显示"或"结束还原"的状态。

- **`BlockInfluenceOrchestrator.java`**：接入 `ImpactVfxTracker`——`contributeAnimation()` 里每帧调用一次（不需要 `world` 参数，VFX 触发不读世界状态），结束清理块里同步调用 `clearInstance`。

- **`RhythmDropEventFactory.java`**（新文件，`timeline/generation/` 包）：把"一组落点（已存在于世界中的真实方块坐标）+ 一组命中时间"转换成对应数量的 `RhythmDrop` ANIMATE 事件。每个事件用 `singleBlockX/Y/Z` 参数指定精确落点（复用 `StepBurstEventFactory.readSingleBlockPos` 已经支持的单方块定向机制），并显式把 `meteorScatter` 设成 `0.0`——**这一点很重要**：`WORLD_TRAJECTORY` 这条路径（`BlockInfluenceEvaluator.applyWorldTrajectory`）默认横向散射是 `2.5`（给 `Meteor` 预设用的，故意做出左右摆动的流星感），如果不显式覆盖成 `0`，天降方块会精确落点失准，没法卡准目标坐标。

**这次故意没做、需要你们后续接上的部分**：

1. `RhythmDropEventFactory` 目前只是一个纯函数式的"落点+时间→事件列表"转换器，**没有接 UI 触发入口**。需要的话，可以参考 `StepBurstEventFactory`/`AutoMapGenerator` 现有的"生成后走 `TimelineDraftWriter.writeEvents(...)` 写入 Timeline"惯例，在合适的面板（比如选中一批落点方块后的右键菜单，或者专门的"生成天降方块"按钮）接一下。
2. `targetObjectId` 参数要求传入一个**已经注册进 `StageObjectSystem`** 的 `StageObject` id（只用它的名字/中心点展示用，不影响实际下落坐标——那个由每个事件自带的 `singleBlock` 参数决定）。调用前需要先注册一个占位用的 `StageObject`（哪怈是个空壳，只要 id 有效即可），所有天降方块事件可以共享同一个引用，不需要每个音符单独注册。
3. 命中时间的来源（"哪些拍子该落音符"）这次也没有绑定具体实现——`RhythmDropEventFactory.build(...)` 接受任意 `List<Double>` 时间序列，可以是 `BeatGridPacing.computeTimestamps(...)` 算出来的结果，也可以是创作者手动在 UI 上拖出来的时间点，按你们觉得哪种创作体验更顺手来接。

---

## 3. 合并后的验证清单

1. **编译**：这次改动没有引入新的第三方依赖，全部是项目内 import。`./gradlew compileJava` 应该能过；如果报错，大概率是某个文件在你们自己代码库里已经和这次的版本产生了分叉（比如 `BlockInfluenceOrchestrator.java` 这种核心调度类，被这次和之前的图层系统先后改过两轮，确认一下两边改动有没有互相覆盖掉对方）。
2. **跑酷踩点回归测试**：触发一次"踩点闪烁"效果，确认：
   - 闪烁期间方块材质确实变了（视觉上能看到）。
   - 闪烁结束后，**真实世界方块状态完全没变过**（用 `/setblock` 之类的命令读一下那个坐标的方块，或者直接用 WorldEdit `//pos1` 检查，确认从头到尾都是原来的材质，不是"改了又还原"，而是"压根没改过"）。
   - 高频连续踩点（密集触发很多次）时，观察是否还有区块重建造成的卡顿——理论上应该完全没有了，因为不再有任何真实 `setBlockState` 调用。
3. **天降方块**：手动构造几个 `RhythmDropEventFactory.build(...)` 调用（或者先接个临时调试按钮），确认方块确实从高处精确落到目标坐标（不会左右摆），落地瞬间有粒子，落地后视觉方块消失、目标方块本身材质和位置完全没变。
4. **图层撤销修复**：隐藏一个图层 → 删除它 → 撤销，确认方块重新隐藏（不是变回可见）。

---

## 4. 给 Cursor 的合并建议

- 这 9 个文件互相之间是一致的（基于同一份代码库改的），建议**整批一次性覆盖**，不要只合并一部分——尤其是 `AnimatedBlock.java`/`BeatBlockAnimatedBlocksRenderer.java`/`AppearancePulseTracker.java`/`BlockInfluenceOrchestrator.java` 这四个是强耦合的一组（场景一修复），分开合并容易出现编译不过或者改动只生效一半的情况。
- 如果合并时发现某个文件在你们自己代码库里已经有别的、不在这份说明里提到的改动（比如你们自己又给 `AppearancePulseTracker` 加了别的通道），优先做**三方合并**而不是直接覆盖，保留你们自己的部分，叠加这次的"渲染层覆盖、不碰真实世界"这条核心改动。
- `BlockInfluenceOrchestrator.java` 是这次改动里冲突风险最高的一个文件（核心调度类，历史上被多轮改动触碰过），合并后建议人工对照一遍，确认 `appearanceTracker`/`impactVfxTracker` 两个字段、`contributeAnimation()` 里两处调用、结束清理块里两处 `clearInstance` 调用都齐全，没有被合并工具误删。
