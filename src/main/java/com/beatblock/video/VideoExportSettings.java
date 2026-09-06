package com.beatblock.video;

import java.nio.file.Path;

/**
 * 视频导出参数。
 * <p>
 * <b>时间区间契约（半开区间）：</b>{@code [startTimeSeconds, endTimeSeconds)}。
 * 帧数为 {@code ceil(duration * fps)}；帧 {@code i} 的时刻为 {@code start + i / fps}，
 * 最后一帧落在 {@code end} 之前，不要改成包含 {@code end}（否则会多出一帧）。
 */
public record VideoExportSettings(
	Path outputPath,
	int width,
	int height,
	int fps,
	double startTimeSeconds,
	double endTimeSeconds,
	boolean includeAudio
) {
	public VideoExportSettings {
		fps = Math.max(1, fps);
		startTimeSeconds = Math.max(0.0, startTimeSeconds);
		endTimeSeconds = Math.max(startTimeSeconds + 0.01, endTimeSeconds);
	}

	/** 导出区间长度（秒）：{@code end - start}（半开区间长度）。 */
	public double durationSeconds() {
		return Math.max(0.01, endTimeSeconds - startTimeSeconds);
	}

	/**
	 * 编码帧数。对应半开区间 {@code [start, end)}：
	 * 时刻为 {@code start + i/fps}，{@code i ∈ [0, totalFrames)}。
	 */
	public int totalFrames() {
		return Math.max(1, (int) Math.ceil(durationSeconds() * fps));
	}

	/**
	 * 与 {@link #totalFrames()} 对齐的媒体时长（秒）：{@code totalFrames / fps}。
	 * 用于 ffmpeg 音频 {@code -t}，使音视频长度一致。
	 */
	public double encodedDurationSeconds() {
		return totalFrames() / (double) fps;
	}
}
