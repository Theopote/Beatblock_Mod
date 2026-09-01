package com.beatblock.client.export;

import com.beatblock.video.VideoExportSettings;

/**
 * 视频导出帧时钟：统一计算某一导出帧对应的时间线时刻与音频源位置。
 * <p>
 * 帧 {@code i} 的时间线时刻为 {@code startTimeSeconds + i / fps}，与
 * {@link VideoExportCoordinator} 的 seek / VFX 合成 / ffmpeg 音频起点对齐。
 */
public final class VideoExportFrameClock {

	private VideoExportFrameClock() {}

	public static double timelineTimeSeconds(VideoExportSettings settings, int frameIndex) {
		validate(settings, frameIndex);
		return settings.startTimeSeconds() + frameIndex / (double) settings.fps();
	}

	public static double timelineTimeSeconds(double exportStartSeconds, int frameIndex, int fps) {
		if (fps <= 0) {
			throw new IllegalArgumentException("fps must be positive");
		}
		if (frameIndex < 0) {
			throw new IllegalArgumentException("frameIndex must be >= 0");
		}
		return Math.max(0.0, exportStartSeconds) + frameIndex / (double) fps;
	}

	/** 导出第 {@code frameIndex} 帧时，应从音频源文件的哪一秒开始读取。 */
	public static double audioSourceTimeSeconds(VideoExportSettings settings, int frameIndex) {
		return timelineTimeSeconds(settings, frameIndex);
	}

	public static long audioSampleIndex(VideoExportSettings settings, int frameIndex, int sampleRate) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("sampleRate must be positive");
		}
		return Math.round(audioSourceTimeSeconds(settings, frameIndex) * sampleRate);
	}

	public static double audioTimeFromSampleIndex(long sampleIndex, int sampleRate) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("sampleRate must be positive");
		}
		return sampleIndex / (double) sampleRate;
	}

	private static void validate(VideoExportSettings settings, int frameIndex) {
		if (settings == null) {
			throw new IllegalArgumentException("settings must not be null");
		}
		if (frameIndex < 0) {
			throw new IllegalArgumentException("frameIndex must be >= 0");
		}
	}
}
