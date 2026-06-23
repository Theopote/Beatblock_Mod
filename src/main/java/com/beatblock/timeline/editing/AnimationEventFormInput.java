package com.beatblock.timeline.editing;

/**
 * 动画事件属性表单（已从 UI 缓冲解析为 typed 值）。
 */
public record AnimationEventFormInput(
	double timeSeconds,
	double durationSeconds,
	float energy,
	float energyThreshold,
	String actionMode,
	String animationId,
	String targetObjectId,
	boolean inheritGroupSpatial,
	String spatialMode,
	double spatialDelaySeconds,
	boolean stepDispatch,
	String stepStartMode,
	String stepCompletionMode,
	String pacingMode,
	int blocksPerBeat,
	double distancePaceSecondsPerBlock,
	double distancePaceMinGapSeconds,
	boolean cameraAdaptiveStep,
	boolean cameraFrustumGating,
	double cameraEdgePriority,
	boolean usePhaseAnimation,
	double entryDurationPercent,
	double idleDurationPercent,
	double exitDurationPercent,
	double cameraNearDistance,
	double cameraFarDistance,
	double cameraNearScale,
	double cameraFarScale,
	String placeBlockId,
	String flashBlockId,
	boolean vfxEnabled
) {}
