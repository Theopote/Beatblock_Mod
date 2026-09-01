package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.video.VideoExportSettings;
import net.minecraft.util.math.Vec3d;

/**
 * 从编译快照采样某一导出帧的逻辑状态，供视频导出与作品级同步回归共用。
 */
public final class VideoExportFrameSampler {

	public static final int DEFAULT_AUDIO_SAMPLE_RATE = 44_100;

	private VideoExportFrameSampler() {}

	public static VideoExportFrameState sample(
		CompiledTimelineSnapshot program,
		VideoExportSettings settings,
		int frameIndex
	) {
		return sample(program, settings, frameIndex, Vec3d.ZERO, 0f, 0f, DEFAULT_AUDIO_SAMPLE_RATE);
	}

	public static VideoExportFrameState sample(
		CompiledTimelineSnapshot program,
		VideoExportSettings settings,
		int frameIndex,
		Vec3d cameraAnchor,
		float fallbackYawDeg,
		float fallbackPitchDeg,
		int audioSampleRate
	) {
		if (program == null) {
			throw new IllegalArgumentException("program must not be null");
		}
		double timelineTime = VideoExportFrameClock.timelineTimeSeconds(settings, frameIndex);
		long audioSampleIndex = VideoExportFrameClock.audioSampleIndex(settings, frameIndex, audioSampleRate);
		double audioSourceTime = VideoExportFrameClock.audioTimeFromSampleIndex(audioSampleIndex, audioSampleRate);

		TimelineCameraEvaluator.CameraSample camera = TimelineCameraEvaluator.evaluate(
			program.cameraTrack(),
			program.bpm(),
			timelineTime,
			cameraAnchor,
			fallbackYawDeg,
			fallbackPitchDeg
		);
		PlaybackStateDigest stageState = PlaybackStateDigest.reconstructAt(program, timelineTime);
		ExportVfxState vfxState = ExportVfxState.resolve(program.globalEvents(), timelineTime);

		return new VideoExportFrameState(
			frameIndex,
			timelineTime,
			audioSampleIndex,
			audioSourceTime,
			camera,
			stageState,
			vfxState
		);
	}
}
