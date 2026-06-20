package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责播放动画：维护当前活跃实例，每帧根据时间线时间更新并对方块应用效果。
 */
public final class AnimationPlayer {

	private final List<EngineAnimationInstance> activeInstances = new ArrayList<>();
	/** 当前帧每块（按 BlockPos 去重）的动画状态，供渲染读取；每帧由 applyAnimation 填充 */
	private final Map<BlockPos, AnimatedBlock> currentFrameBlocks = new HashMap<>();

	public List<EngineAnimationInstance> getActiveInstances() {
		return new ArrayList<>(activeInstances);
	}

	public void addInstance(EngineAnimationInstance instance) {
		if (instance != null) activeInstances.add(instance);
	}

	public void removeEnded(double timelineTimeSeconds) {
		activeInstances.removeIf(inst -> !inst.isActiveAt(timelineTimeSeconds));
	}

	/**
	 * 每帧调用：根据时间线时间更新所有活跃实例，对目标方块的 AnimatedBlock 应用效果。
	 * 执行后可通过 getCurrentFrameBlocks() 取得当前帧每块的状态用于渲染。
	 *
	 * @deprecated 由 {@link com.beatblock.engine.influence.BlockInfluenceOrchestrator} 统一求值
	 */
	@Deprecated
	public void update(double timelineTimeSeconds) {
		currentFrameBlocks.clear();
		for (EngineAnimationInstance anim : activeInstances) {
			if (!anim.isActiveAt(timelineTimeSeconds)) continue;
			applyAnimation(anim, anim.getProgress(timelineTimeSeconds));
		}
	}

	public void replaceCurrentFrameBlocks(Map<BlockPos, AnimatedBlock> blocks) {
		currentFrameBlocks.clear();
		if (blocks == null || blocks.isEmpty()) return;
		for (Map.Entry<BlockPos, AnimatedBlock> entry : blocks.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				currentFrameBlocks.put(entry.getKey().toImmutable(), entry.getValue());
			}
		}
	}

	/**
	 * 对单条实例应用 preset（测试 / 过渡用）。
	 */
	void applyAnimation(EngineAnimationInstance anim, float t) {
		if (anim == null || anim.getDefinition() == null || anim.getDefinition().getPreset() == null) return;
		StageObject target = anim.getTarget();
		if (target == null) return;
		float energy = anim.getEnergy();
		EffectContext ctx = new EffectContext(target.getCenter(), anim.getExtraParams());
		var evaluator = new com.beatblock.engine.influence.BlockInfluenceEvaluator();
		for (BlockPos pos : target.getBlocks()) {
			AnimatedBlock block = currentFrameBlocks.computeIfAbsent(pos.toImmutable(), AnimatedBlock::new);
			block.resetToOriginal();
			evaluator.applyPreset(block, anim.getDefinition().getPreset(), t, energy, ctx);
		}
	}

	/** 当前帧参与动画的方块及其状态（只读），渲染层可据此做 Matrix 变换后绘制 */
	public Map<BlockPos, AnimatedBlock> getCurrentFrameBlocks() {
		return currentFrameBlocks;
	}

	public void clear() {
		activeInstances.clear();
		currentFrameBlocks.clear();
	}
}
