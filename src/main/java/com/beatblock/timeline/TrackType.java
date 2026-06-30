package com.beatblock.timeline;

/**
 * 轨道类型：对应编辑器中的一条轨道。
 */
public enum TrackType {
	AUDIO,
	CAMERA,
	ANIMATION,
	/** 接收建造图层片段，按播放头驱动 BUILD 揭示。 */
	BUILD_LAYER,
	EVENT,
	PARTICLE
}
