package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.timeline.playback.ResolvedStageState;
import com.beatblock.timeline.playback.StageStateResolver;
import com.beatblock.video.VideoExportSettings;
import net.minecraft.util.math.Vec3d;

/**
 * 导出帧语义权威：从冻结编译快照采样某一帧的 Camera / Stage / VFX / Audio。
 * <p>
 * Stage / VFX 经 {@link StageStateResolver} 解析，与 Scrub / Formal Seek 共用同一逻辑态。
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

		ResolvedStageState stage = StageStateResolver.resolve(
			program, timelineTime, cameraAnchor, fallbackYawDeg, fallbackPitchDeg);
		TimelineCameraEvaluator.CameraSample camera = stage.camera() != null
			? stage.camera()
			: TimelineCameraEvaluator.evaluate(
				program.cameraTrack(),
				program.bpm(),
				timelineTime,
				cameraAnchor,
				fallbackYawDeg,
				fallbackPitchDeg
			);

		return new VideoExportFrameState(
			frameIndex,
			timelineTime,
			audioSampleIndex,
			audioSourceTime,
			camera,
			stage.toPlaybackDigest(),
			stage.vfxState()
		);
	}
}
