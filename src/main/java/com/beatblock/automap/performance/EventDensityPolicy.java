package com.beatblock.automap.performance;

/**
 * 事件密度策略：决定节奏过滤松紧，拉开预设气质。
 */
public enum EventDensityPolicy {
	/** 拍点纹理：更密、更跟 Kick/Snare/HiHat。 */
	BEAT_HEAVY,
	/** 段落感知：中等密度，尊重 section density curve。 */
	SECTION_AWARE,
	/** 电影稀疏：少而准，给 Build/Camera 留空间。 */
	SPARSE_CINEMATIC
}
