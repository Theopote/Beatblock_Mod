package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.WaveformExtractor;
import com.beatblock.audio.analysis.structure.BarGridBuilder;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.audio.analysis.structure.NoveltyCurve;
import com.beatblock.audio.analysis.structure.SectionLabeler;
import com.beatblock.audio.analysis.structure.SelfSimilarityMatrix;
import com.beatblock.audio.analysis.structure.StructureBoundaryDetector;
import com.beatblock.audio.analysis.structure.StructureFeatureExtractor;
import com.beatblock.audio.analysis.structure.StructureFeatureFrame;

import java.util.List;

/**
 * 音乐结构分析门面：Beat → Bar → Phrase → Section。
 * <p>
 * 段落边界主要依赖 novelty 曲线与自相似矩阵（checkerboard），并结合能量 / onset 打标签；
 * 重复段落用于识别 Verse / Chorus，而非固定时间窗口。
 */
public final class MusicStructureAnalyzer {

	private static final double SMOOTH_WINDOW_SECONDS = 2.0;
	private static final int CHECKERBOARD_RADIUS = 6;

	private MusicStructureAnalyzer() {}

	public static MusicStructure analyze(AudioFeatureTimeline timeline) {
		if (timeline == null || timeline.getDurationSeconds() <= 0) {
			return MusicStructure.fallback(timeline != null ? timeline.getDurationSeconds() : 0);
		}

		List<StructureFeatureFrame> frames = StructureFeatureExtractor.extract(timeline);
		if (frames.isEmpty()) {
			return MusicStructure.fallback(timeline.getDurationSeconds());
		}

		double[] novelty = NoveltyCurve.compute(frames);
		double[] smoothed = NoveltyCurve.smooth(novelty, frames, SMOOTH_WINDOW_SECONDS);
		double[][] similarity = SelfSimilarityMatrix.compute(frames);
		double[] checkerboard = SelfSimilarityMatrix.checkerboardNovelty(similarity, CHECKERBOARD_RADIUS);

		BeatGrid grid = timeline.getBeatGrid();
		double barDuration = BarGridBuilder.barDuration(grid);
		List<BarGridBuilder.BarSpan> bars = BarGridBuilder.build(grid, timeline.getDurationSeconds());
		List<Double> phraseBounds = StructureBoundaryDetector.detectPhraseBoundaries(
			smoothed, frames, barDuration);
		List<Double> sectionBounds = StructureBoundaryDetector.detectSectionBoundaries(
			smoothed, checkerboard, frames, barDuration, timeline.getDurationSeconds());

		List<StructuralSection> sections = SectionLabeler.label(
			frames, similarity, sectionBounds, timeline.getDurationSeconds());
		List<MusicStructure.PhraseSpan> phrases = MusicStructure.buildPhrases(phraseBounds, frames, similarity);

		return new MusicStructure(
			timeline.getDurationSeconds(),
			MusicStructure.collectBeatTimes(grid, timeline.getBeats(), timeline.getDurationSeconds()),
			bars,
			phrases,
			sections
		);
	}

	public static List<StructuralSection> analyzeSections(AudioFeatureTimeline timeline) {
		return analyze(timeline).sections();
	}

	/** 兼容旧 API：仅能量帧时退化为简化特征分析。 */
	public static List<StructuralSection> analyze(List<EnergyFrame> energyFrames, double durationSeconds) {
		AudioFeatureTimeline timeline = new AudioFeatureTimeline(
			durationSeconds,
			List.of(),
			energyFrames != null ? energyFrames : List.of(),
			List.of(),
			new WaveformExtractor.WaveformFrame[0],
			0f,
			null
		);
		return analyze(timeline).sections();
	}
}
