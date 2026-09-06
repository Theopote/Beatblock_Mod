package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.video.VideoExportSettings;
import net.minecraft.util.math.Vec3d;

/**
 * 导出帧语义权威：从冻结编译快照采样某一帧的 Camera / Stage / VFX / Audio。
 * <p>
 * 生产链（{@link VideoExportCoordinator}）与同步回归测试应共用本采样结果并应用之，
 * 而不是各自独立 evaluate。舞台世界写入仍由 Driver seek 完成；逻辑 digest 以本类为准。
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
