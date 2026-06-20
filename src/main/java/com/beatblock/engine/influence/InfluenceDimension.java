package com.beatblock.engine.influence;

/**
 * 对方块的影响维度。未启用的维度不参与 preset 求值。
 */
public enum InfluenceDimension {
	EXISTENCE,
	TRANSFORM_POSITION,
	TRANSFORM_ROTATION,
	TRANSFORM_SCALE,
	APPEARANCE,
	VFX
}
