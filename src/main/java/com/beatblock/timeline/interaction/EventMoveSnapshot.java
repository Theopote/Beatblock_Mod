package com.beatblock.timeline.interaction;

/** 一次事件时间移动的快照，用于组拖提交 Undo。 */
public record EventMoveSnapshot(
	String trackId,
	String clipId,
	String eventId,
	double oldTimeSeconds,
	double newTimeSeconds
) {}
