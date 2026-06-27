package com.beatblock.ui.presenter;

import java.util.Map;

/**
 * 事件属性表单绑定快照：由 Presenter 从 Timeline 数据生成，Panel 写入 ImGui 缓冲。
 */
public record EventPropertiesFormSnapshot(
	String refKey,
	String camClipStart,
	String camClipEnd,
	boolean camClipPathVisible,
	String time,
	String duration,
	String energy,
	String energyThreshold,
	String spatialDelay,
	String blocksPerBeat,
	String distancePaceSeconds,
	String distancePaceMinGap,
	String cameraNearDistance,
	String cameraFarDistance,
	String cameraNearScale,
	String cameraFarScale,
	String cameraEdgePriority,
	String placeBlock,
	String flashBlock,
	String camSegDuration,
	boolean camSegPathVisible,
	Map<String, String> camSegParams,
	String camX,
	String camY,
	String camZ,
	String camYaw,
	String camPitch,
	String camEase,
	String singleBlockX,
	String singleBlockY,
	String singleBlockZ,
	String meteorHeight,
	String meteorScatter,
	String impactThreshold
) {}
