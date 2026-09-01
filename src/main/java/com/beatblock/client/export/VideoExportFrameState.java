package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.PlaybackStateDigest;

/** 单帧导出时刻的确定性逻辑状态（镜头 / 舞台 / VFX / 音频对齐）。 */
public record VideoExportFrameState(
	int frameIndex,
	double timelineTimeSeconds,
	long audioSampleIndex,
	double audioSourceTimeSeconds,
	TimelineCameraEvaluator.CameraSample camera,
	PlaybackStateDigest stageState,
	ExportVfxState vfxState
) {}
