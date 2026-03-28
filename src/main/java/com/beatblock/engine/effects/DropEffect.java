package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
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
		double y = t * h * energy;
		double xOff = scatter * Math.sin(pos.x * 3.7 + t * 2) * energy;
		double zOff = scatter * Math.cos(pos.z * 3.7 + t * 2) * energy;
		block.setPosition(pos.x + xOff, pos.y - y, pos.z + zOff);
	}
}
