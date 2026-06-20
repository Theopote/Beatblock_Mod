package com.beatblock.engine;

/**
 * 动画由 preset 通道组合而成；期 1 遗留的 {@code AnimationEffect} 实现类已不再注册。
 *
 * @deprecated 由 {@link com.beatblock.engine.influence.BlockInfluenceEvaluator} 替代
 */
@Deprecated
public interface AnimationEffect {

	/**
	 * 对单块应用本效果（可修改 block 的 position、velocity、rotation、scale）。
	 *
	 * @param block 当前块的动画状态（每帧会先 resetToOriginal 再依次应用各 effect）
	 * @param time  动画进度 0～1
	 * @param energy 音乐能量 0～1
	 * @param ctx   上下文（如舞台中心，供 Explosion/Spiral/Orbit 使用）
	 */
	void apply(AnimatedBlock block, float time, float energy, EffectContext ctx);
}
