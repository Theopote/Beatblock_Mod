package com.beatblock.timeline;

/**
 * 时间线事件类型：与 UI 展示、序列化、插件扩展一致。
 */
public enum EventType {
	BEAT,
	ANIMATION,
	CAMERA_KEYFRAME,
	/** 镜头片段定义（与 {@link #CAMERA_KEYFRAME} 同属摄像机轨；参数含 kind 等） */
	CAMERA_SEGMENT,
	PARTICLE,
	LIGHTING,
	GLOBAL
}
