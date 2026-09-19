package com.beatblock.audio.analysis;

import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.automap.choreography.BeatmapStructureAdapter;
import com.beatblock.automap.engine.MusicStructureAnalyzer;
import com.beatblock.audio.analysis.structure.MusicStructure;

import org.jspecify.annotations.Nullable;

/**
 * 统一音乐分析资产：Creator / Smart AutoMap 只消费此模型，不关心 Python 还是 Java 产出。
 */
public final class MusicAnalysisAsset {

	public enum Source {
		JAVA_FEATURE,
		PYTHON_BEATMAP,
		MERGED
	}

	private final String fingerprint;
	private final AudioFeatureTimeline featureTimeline;
	private final @Nullable Beatmap beatmap;
	private final AudioAnalysisMode analysisMode;
	private final double confidence;
	private final Source source;

	public MusicAnalysisAsset(
		String fingerprint,
		AudioFeatureTimeline featureTimeline,
		@Nullable Beatmap beatmap,
		AudioAnalysisMode analysisMode,
		double confidence,
		Source source
	) {
		this.fingerprint = fingerprint != null ? fingerprint : "";
		this.featureTimeline = featureTimeline;
		this.beatmap = beatmap;
		this.analysisMode = analysisMode != null ? analysisMode : AudioAnalysisMode.BASIC;
		this.confidence = Math.max(0.0, Math.min(1.0, confidence));
		this.source = source != null ? source : Source.JAVA_FEATURE;
	}

	public String fingerprint() {
		return fingerprint;
	}

	public AudioFeatureTimeline featureTimeline() {
		return featureTimeline;
	}

	public @Nullable Beatmap beatmap() {
		return beatmap;
	}

	public AudioAnalysisMode analysisMode() {
		return analysisMode;
	}

	public double confidence() {
		return confidence;
	}

	public Source source() {
		return source;
	}

	public double durationSeconds() {
		return featureTimeline != null ? featureTimeline.getDurationSeconds() : 0.0;
	}

	public float bpm() {
		return featureTimeline != null ? featureTimeline.getBpm() : 0f;
	}

	/** 结构优先来自 Beatmap；否则从 FeatureTimeline 再分析。 */
	public MusicStructure musicalStructure() {
		if (beatmap != null) {
			return BeatmapStructureAdapter.fromBeatmap(beatmap, featureTimeline);
		}
		return MusicStructureAnalyzer.analyze(featureTimeline);
	}

	public boolean isUsable() {
		return featureTimeline != null && featureTimeline.getDurationSeconds() > 0;
	}
}
