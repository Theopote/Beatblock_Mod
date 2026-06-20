package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationPlayer;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.BuildSequencer;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 统一帧编排：动画 preset 求值 + 建造 EXISTENCE mutation，单入口 apply。
 */
public final class BlockInfluenceOrchestrator {

	private final BlockControlExecutor blockControlExecutor;
	private final BlockInfluenceEvaluator evaluator = new BlockInfluenceEvaluator();

	public BlockInfluenceOrchestrator(BlockControlExecutor blockControlExecutor) {
		this.blockControlExecutor = blockControlExecutor;
	}

	public InfluenceFrame computeFrame(
		double timelineTimeSeconds,
		AnimationPlayer animationPlayer,
		BuildSequencer buildSequencer,
		World world
	) {
		InfluenceFrame frame = new InfluenceFrame();
		if (animationPlayer != null) {
			for (EngineAnimationInstance instance : animationPlayer.getActiveInstances()) {
				if (!instance.isActiveAt(timelineTimeSeconds)) continue;
				contributeAnimation(frame, instance, timelineTimeSeconds);
			}
		}
		if (buildSequencer != null && world != null) {
			buildSequencer.contributeExistenceMutations(frame, timelineTimeSeconds, world);
		}
		return frame;
	}

	public void applyFrame(InfluenceFrame frame, AnimationPlayer animationPlayer, World world) {
		if (animationPlayer != null && frame != null) {
			animationPlayer.replaceCurrentFrameBlocks(frame.getAnimatedBlocks());
		}
		if (world != null && frame != null && blockControlExecutor != null) {
			blockControlExecutor.applyMutations(world, frame.getWorldMutations());
		}
	}

	/**
	 * 计算并应用单帧影响；返回本帧世界 mutation 数量。
	 */
	public int tick(
		double timelineTimeSeconds,
		AnimationPlayer animationPlayer,
		BuildSequencer buildSequencer,
		World world
	) {
		InfluenceFrame frame = computeFrame(timelineTimeSeconds, animationPlayer, buildSequencer, world);
		applyFrame(frame, animationPlayer, world);
		return frame != null ? frame.getWorldMutations().size() : 0;
	}

	private void contributeAnimation(
		InfluenceFrame frame,
		EngineAnimationInstance instance,
		double timelineTimeSeconds
	) {
		if (instance.getTarget() == null || instance.getDefinition() == null) return;
		BlockInfluencePreset preset = instance.getDefinition().getPreset();
		if (preset == null) return;

		float t = instance.getProgress(timelineTimeSeconds);
		float energy = instance.getEnergy();
		EffectContext ctx = new EffectContext(instance.getTarget().getCenter(), instance.getExtraParams());

		for (BlockPos pos : instance.getTarget().getBlocks()) {
			var block = frame.animatedBlockFor(pos);
			block.resetToOriginal();
			evaluator.applyPreset(block, preset, t, energy, ctx);
		}
	}
}
