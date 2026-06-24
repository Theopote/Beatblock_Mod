package com.beatblock.ui.presenter;

/**
 * 动画事件属性面板渲染所需的当前模型状态（下拉索引、勾选框等）。
 */
public record AnimationEditorViewState(
	String animationId,
	String targetId,
	String actionMode,
	boolean inheritGroupSpatial,
	boolean stepDispatch,
	boolean cameraAdaptiveStep,
	boolean cameraFrustumGating,
	boolean usePhaseAnimation,
	boolean vfxEnabled,
	String stepStartMode,
	String stepCompletionMode,
	String pacingMode,
	String spatialMode,
	String mappingProfile,
	String sourceStem,
	String sourceFeature,
	String generatedBy
) {}
