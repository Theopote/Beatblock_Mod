package com.beatblock.engine.influence;

/**
 * 归一化时间 {@code t ∈ [0,1]} 上的插值曲线种类。
 */
public enum CurveKind {
	/** {@code t} */
	LINEAR,
	/** {@code 1 - t} */
	INVERSE_LINEAR,
	/** {@code sin(tπ)}，0→1→0 */
	SINE_BUMP,
	/** {@code 1 - t²}，重力感剩余高度 */
	GRAVITY_REMAINING,
	CONST_ZERO,
	CONST_ONE
}
