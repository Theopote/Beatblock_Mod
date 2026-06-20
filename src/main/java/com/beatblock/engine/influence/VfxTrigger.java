package com.beatblock.engine.influence;

import net.minecraft.util.math.BlockPos;

/**
 * 粒子/光效触发点（期 3 由 {@code VfxEmitter} 消费）。
 */
public record VfxTrigger(
	String kind,
	BlockPos blockPos,
	double timeSeconds,
	float intensity
) {}
