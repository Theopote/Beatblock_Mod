package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.AnimationEffect;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.influence.CurveLibrary;
import net.minecraft.util.math.Vec3d;

/**
 * 流星坠落：方块从高空（原位 + height）以重力曲线砸向原始位置。
 *
 * <p>运动曲线使用二次加速（t²），模拟重力加速度：
 * <ul>
 *   <li>t=0：方块在起始高空，带横向入射散射偏移</li>
 *   <li>t=1：方块落回原始位置，无偏移</li>
 * </ul>
 *
 * <p>支持的 extraParams：
 * <ul>
 *   <li>{@code meteorHeight} (double, 默认 12.0) — 起始高度偏移（方块数），覆盖构造参数</li>
 *   <li>{@code meteorScatter} (double, 默认 2.5) — 横向切入散射半径（方块数），覆盖构造参数</li>
 * </ul>
 */
public final class MeteorEffect implements AnimationEffect {

	private final float height;
	private final float scatter;

	public MeteorEffect(float height, float scatter) {
		this.height = Math.max(0f, height);
		this.scatter = Math.max(0f, scatter);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float h = (float) ctx.paramDouble("meteorHeight", height);
		float sc = (float) ctx.paramDouble("meteorScatter", scatter);

		Vec3d pos = block.getPosition();
		double fall = CurveLibrary.gravityRemainingHeight(t, h, energy);

		double xOff = sc > 0f
			? sc * Math.sin(pos.x * 1.9 + pos.z * 0.7) * CurveLibrary.scatterEnvelope(t, energy)
			: 0.0;
		double zOff = sc > 0f
			? sc * Math.cos(pos.z * 1.9 + pos.x * 0.7) * CurveLibrary.scatterEnvelope(t, energy)
			: 0.0;

		block.setPosition(pos.x + xOff, pos.y + fall, pos.z + zOff);
		block.setScale(block.getScale() * CurveLibrary.meteorApproachScale(t, energy));
	}
}
