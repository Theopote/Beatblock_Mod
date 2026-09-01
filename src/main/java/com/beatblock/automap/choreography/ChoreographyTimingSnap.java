package com.beatblock.automap.choreography;

/**
 * 编舞事件时间对齐粒度：由 Rule / Phrase 指定，编译期按需量化。
 */
public enum ChoreographyTimingSnap {
	/** 保持原始节拍检测时间（Kick / HiHat / off-beat groove）。 */
	NONE,
	/** 对齐到最近拍点。 */
	BEAT,
	/** 对齐到半拍网格。 */
	HALF_BEAT,
	/** 对齐到四分拍（16th note）网格。 */
	QUARTER_BEAT,
	/** 对齐到最近小节起点（Build / Camera cut 等结构点）。 */
	BAR,
	/** 对齐到最近乐句起点。 */
	PHRASE,
	/** 对齐到最近 Section 起点（Drop flash 等宏观结构）。 */
	SECTION
}
