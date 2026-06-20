package com.beatblock.engine.influence;

/**
 * 通道值在块局部空间中的解释方式（期 1 以数据枚举为主，期 2 由求值器消费）。
 */
public enum PathKind {
	OFFSET_Y,
	OFFSET_XZ,
	WORLD_TRAJECTORY,
	RADIAL_FROM_CENTER,
	ORBIT_HORIZONTAL,
	SPIRAL_LIFT,
	WAVE_Y,
	SCALE_UNIFORM,
	BLOCK_STATE,
	VISIBLE
}
