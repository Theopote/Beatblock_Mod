package com.beatblock.audio.beatmap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 第 1 层 — 音频分析契约（磁盘缓存，只读导入）。
 * <p>
 * Python {@code analyze.py} 输出的 .beatmap JSON。经
 * {@link com.beatblock.audio.analysis.AudioAnalysisEngine#fillTimelineFromBeatmap}
 * 一次性写入时间轴参考轨后，<strong>不参与播放</strong>。
 */
public final class Beatmap {

	/** 契约版本号，与 Python 脚本保持一致 */
	public final int version;
	public final @NonNull BeatmapMeta meta;
	public final @NonNull List<BeatEvent> beats;
	public final @NonNull List<MusicSection> sections;
	/** 可选：UI 波形预览数据，可能为 null */
	public final @Nullable WaveformPreview waveformPreview;
	/** 可选：每条茎的独立波形预览，key = stem name (drums/bass/vocals/other) */
	public final @NonNull Map<String, WaveformPreview> stemWaveforms;

	/**
	 * 运行时元数据：该 beatmap 文件的绝对路径（读取后由 BeatmapReader 设置）。
	 * 用于解析茎音频 WAV 文件的相对路径。不参与序列化。
	 */
	public @Nullable Path beatmapFilePath;

	public Beatmap(
		int version,
		@NonNull BeatmapMeta meta,
		@NonNull List<BeatEvent> beats,
		@NonNull List<MusicSection> sections,
		@Nullable WaveformPreview waveformPreview,
		@Nullable Map<String, WaveformPreview> stemWaveforms
	) {
		this.version         = version;
		this.meta            = meta;
		this.beats           = List.copyOf(beats);
		this.sections        = List.copyOf(sections);
		this.waveformPreview = waveformPreview;
		this.stemWaveforms   = stemWaveforms != null ? Map.copyOf(stemWaveforms) : Map.of();
	}

	// ── 便捷查询 ─────────────────────────────────────────────────────────

	/** 返回指定频段的踩点列表 */
	public @NonNull List<BeatEvent> beatsForBand(@NonNull FrequencyBand band) {
		return beats.stream()
			.filter(b -> b.band() == band)
			.toList();
	}

	/** 返回指定时间范围内的踩点（start 含，end 不含，单位 ms）*/
	public @NonNull List<BeatEvent> beatsInRange(long startMs, long endMs) {
		return beats.stream()
			.filter(b -> b.timeMs() >= startMs && b.timeMs() < endMs)
			.toList();
	}

	/** 找出 timeMs 所属的段落，找不到返回 null */
	public @Nullable MusicSection sectionAt(long timeMs) {
		for (MusicSection s : sections) {
			if (timeMs >= s.startMs() && timeMs < s.endMs()) return s;
		}
		return null;
	}
}
