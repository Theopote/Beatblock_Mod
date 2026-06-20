package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.AnimationEffect;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.influence.CurveLibrary;
import net.minecraft.util.math.Vec3d;

/**
 * 方块落下：y -= t * height * energy（与 Rise 反向）
 */
public final class DropEffect implements AnimationEffect {

	private final float height;

	public DropEffect(float height) {
		this.height = Math.max(0f, height);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float h = (float) ctx.paramDouble("meteorHeight", height);
		float scatter = (float) ctx.paramDouble("meteorScatter", 0.0);
		Vec3d pos = block.getPosition();
		float y = CurveLibrary.linearProgress(t, h, energy);
		float e = Math.max(0f, energy);
		double xOff = scatter > 0f ? scatter * Math.sin(pos.x * 3.7 + t * 2) * e : 0.0;
		double zOff = scatter > 0f ? scatter * Math.cos(pos.z * 3.7 + t * 2) * e : 0.0;
		block.setPosition(pos.x + xOff, pos.y - y, pos.z + zOff);
	}
}
