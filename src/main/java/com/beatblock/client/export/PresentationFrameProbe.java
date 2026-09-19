package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import net.minecraft.util.math.Vec3d;

/**
 * 与 {@link VideoExportFrameSampler} 同语义的呈现探针，供 Preview ↔ Export 一致性回归复用。
 */
public final class PresentationFrameProbe {

	private PresentationFrameProbe() {}

	public record Frame(
		double timelineTimeSeconds,
		long audioSampleIndex,
		double audioSourceTimeSeconds,
		TimelineCameraEvaluator.CameraSample camera,
		PlaybackStateDigest stageState,
		ExportVfxState vfxState
	) {}

	public static Frame atTime(
		CompiledTimelineSnapshot program,
		double timelineTimeSeconds,
		Vec3d cameraAnchor,
		float fallbackYawDeg,
		float fallbackPitchDeg,
		int audioSampleRate
	) {
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}
		long audioSampleIndex = Math.round(Math.max(0.0, timelineTimeSeconds) * audioSampleRate);
		double audioSourceTime = VideoExportFrameClock.audioTimeFromSampleIndex(audioSampleIndex, audioSampleRate);

		return new Frame(
			timelineTimeSeconds,
			audioSampleIndex,
			audioSourceTime,
			TimelineCameraEvaluator.evaluate(
				program.cameraTrack(),
				program.bpm(),
				timelineTimeSeconds,
				cameraAnchor,
				fallbackYawDeg,
				fallbackPitchDeg
			),
			PlaybackStateDigest.reconstructAt(program, timelineTimeSeconds),
			ExportVfxState.resolve(program.globalEvents(), timelineTimeSeconds)
		);
	}
}
