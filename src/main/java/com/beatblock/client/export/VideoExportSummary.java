package com.beatblock.client.export;

import com.beatblock.timeline.CameraKeyframe;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.video.VideoExportPresets;
import com.beatblock.video.VideoExportSettings;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Creator 视角 Export Summary：按 Export 前回答 “What exactly am I exporting?”
 * <p>
 * 时间区间遵循半开契约 {@code [start, end)}，与 {@link VideoExportSettings} 一致。
 */
public final class VideoExportSummary {

	private VideoExportSummary() {}

	public record Snapshot(
		double startSeconds,
		double endSeconds,
		double durationSeconds,
		int totalFrames,
		int fps,
		int width,
		int height,
		boolean nativeResolution,
		boolean includeAudio,
		@Nullable String audioFileName,
		boolean cameraTimelineDriven,
		boolean vfxEnabled,
		int stageEventsInRange,
		int cameraKeyframesInRange,
		int vfxEventsInRange,
		int markersInRange,
		@Nullable String outputPath,
		@Nullable Double estimatedSizeMb
	) {
		/** {@code 00:10.000 → 01:24.500}（半开区间的起止显示）。 */
		public String rangeSpanLabel() {
			return formatClock(startSeconds) + " → " + formatClock(endSeconds);
		}

		/** {@code 1m 14.5s} / {@code 14.5s}。 */
		public String durationHumanLabel() {
			return formatDurationHuman(durationSeconds);
		}

		public String resolutionLabel() {
			if (nativeResolution || width <= 0 || height <= 0) {
				return BBTexts.get("beatblock.export.summary.video_native");
			}
			return width + " × " + height;
		}

		public String fpsLabel() {
			return BBTexts.get("beatblock.export.summary.video_fps", fps);
		}

		public String framesLabel() {
			return BBTexts.get("beatblock.export.summary.video_frames", formatGrouped(totalFrames));
		}

		public String cameraLabel() {
			return cameraTimelineDriven
				? BBTexts.get("beatblock.export.summary.camera_timeline")
				: BBTexts.get("beatblock.export.summary.camera_none");
		}

		public String audioFileLabel() {
			if (!includeAudio) {
				return BBTexts.get("beatblock.export.summary.audio_none");
			}
			if (audioFileName == null || audioFileName.isBlank()) {
				return BBTexts.get("beatblock.export.summary.audio_unknown");
			}
			return audioFileName;
		}

		public String audioStatusLabel() {
			return includeAudio
				? BBTexts.get("beatblock.export.summary.audio_included")
				: BBTexts.get("beatblock.export.summary.audio_excluded");
		}

		public String vfxLabel() {
			return vfxEnabled
				? BBTexts.get("beatblock.export.summary.vfx_enabled")
				: BBTexts.get("beatblock.export.summary.vfx_disabled");
		}

		public String outputLabel() {
			return outputPath != null && !outputPath.isBlank()
				? outputPath
				: BBTexts.get("beatblock.export.summary.output_unset");
		}
	}

	public static Snapshot build(
		@Nullable Timeline timeline,
		double startSeconds,
		double endSeconds,
		int width,
		int height,
		int fps,
		boolean includeAudio,
		@Nullable String audioPath,
		@Nullable String outputPath,
		@Nullable String ignoredPictureLabel
	) {
		double start = Math.max(0.0, startSeconds);
		double end = Math.max(start + 0.01, endSeconds);
		int safeFps = Math.max(1, fps);
		VideoExportSettings settings = new VideoExportSettings(
			Path.of(outputPath != null && !outputPath.isBlank() ? outputPath : "export.mp4"),
			width,
			height,
			safeFps,
			start,
			end,
			includeAudio
		);

		int stage = 0;
		int camera = 0;
		int vfx = 0;
		int markers = 0;
		int cameraTotal = 0;
		int vfxTotal = 0;
		if (timeline != null) {
			for (TimelineAnimationEvent event : timeline.getStageEvents()) {
				if (event != null && inRange(event.getTimeSeconds(), start, end)) {
					stage++;
				}
			}
			for (CameraKeyframe keyframe : timeline.getCameraKeyframes()) {
				if (keyframe == null) {
					continue;
				}
				cameraTotal++;
				if (inRange(keyframe.getTimeSeconds(), start, end)) {
					camera++;
				}
			}
			for (GlobalEvent event : timeline.getGlobalEvents()) {
				if (event == null) {
					continue;
				}
				vfxTotal++;
				if (inRange(event.getTimeSeconds(), start, end)) {
					vfx++;
				}
			}
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker != null && inRange(marker.getTimeSeconds(), start, end)) {
					markers++;
				}
			}
		}

		boolean nativeRes = width <= 0 || height <= 0;
		boolean cameraDriven = cameraTotal > 0;
		boolean vfxEnabled = vfxTotal > 0;

		String audioFileName = null;
		if (includeAudio && audioPath != null && !audioPath.isBlank()) {
			try {
				Path audio = Path.of(audioPath);
				audioFileName = audio.getFileName() != null ? audio.getFileName().toString() : audioPath;
			} catch (RuntimeException ignored) {
				audioFileName = audioPath;
			}
		}

		String absoluteOutput = null;
		if (outputPath != null && !outputPath.isBlank()) {
			try {
				absoluteOutput = Path.of(outputPath).toAbsolutePath().normalize().toString();
			} catch (RuntimeException ignored) {
				absoluteOutput = outputPath;
			}
		}

		Double estimate = null;
		int estW = nativeRes ? 1920 : width;
		int estH = nativeRes ? 1080 : height;
		if (settings.durationSeconds() > 0) {
			estimate = VideoExportPresets.estimateFileSize(estW, estH, safeFps, settings.durationSeconds());
		}

		return new Snapshot(
			settings.startTimeSeconds(),
			settings.endTimeSeconds(),
			settings.durationSeconds(),
			settings.totalFrames(),
			safeFps,
			nativeRes ? 0 : width,
			nativeRes ? 0 : height,
			nativeRes,
			includeAudio,
			audioFileName,
			cameraDriven,
			vfxEnabled,
			stage,
			camera,
			vfx,
			markers,
			absoluteOutput,
			estimate
		);
	}

	/** 半开区间 {@code [start, end)}。 */
	static boolean inRange(double timeSeconds, double start, double end) {
		return Double.isFinite(timeSeconds) && timeSeconds >= start && timeSeconds < end;
	}

	/** {@code MM:SS.mmm}；超过 1 小时用 {@code H:MM:SS.mmm}。 */
	static String formatClock(double seconds) {
		double safe = Math.max(0.0, seconds);
		int totalMillis = (int) Math.round(safe * 1000.0);
		int millis = totalMillis % 1000;
		int totalSeconds = totalMillis / 1000;
		int secs = totalSeconds % 60;
		int totalMinutes = totalSeconds / 60;
		int mins = totalMinutes % 60;
		int hours = totalMinutes / 60;
		if (hours > 0) {
			return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, mins, secs, millis);
		}
		return String.format(Locale.ROOT, "%02d:%02d.%03d", mins, secs, millis);
	}

	/** {@code 1m 14.5s} / {@code 14.5s} / {@code 1h 2m 3.0s}。 */
	static String formatDurationHuman(double seconds) {
		double safe = Math.max(0.0, seconds);
		int whole = (int) Math.floor(safe);
		double frac = safe - whole;
		int hours = whole / 3600;
		int minutes = (whole % 3600) / 60;
		double secs = (whole % 60) + frac;
		StringBuilder out = new StringBuilder();
		if (hours > 0) {
			out.append(hours).append('h').append(' ');
		}
		if (minutes > 0 || hours > 0) {
			out.append(minutes).append('m').append(' ');
		}
		out.append(String.format(Locale.ROOT, "%.1fs", secs));
		return out.toString().trim();
	}

	static String formatGrouped(int value) {
		return String.format(Locale.US, "%,d", value);
	}
}
