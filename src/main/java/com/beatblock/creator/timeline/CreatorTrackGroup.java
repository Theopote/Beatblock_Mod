package com.beatblock.creator.timeline;

/**
 * 创作者时间线的一级作品轨道概念。
 * <p>
 * 纯视图模型：不对应持久化 track id，仅用于 UI 分组与默认布局。
 */
public enum CreatorTrackGroup {
	MUSIC,
	MUSIC_ANALYSIS,
	PERFORMANCE,
	BUILD,
	CAMERA,
	VFX
}
