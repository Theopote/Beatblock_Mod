package com.beatblock.automap.engine;

import com.beatblock.automap.AutoMapGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节奏模式生成：根据段落类型与复杂度决定是否采纳某节奏事件、最小间隔等。
 * <p>
 * 过滤阶段按归一化频段（Kick→low、Snare→mid、HiHat→high）分别维护 lastTime，
 * 与 {@link com.beatblock.automap.AutoMapCandidateResolver} 的 per-feature minGap 策略一致。
 */
public final class PatternGenerator {

	public static final double DEFAULT_LOW_GAP = 0.12;
	public static final double DEFAULT_MID_GAP = 0.08;
	public static final double DEFAULT_HIGH_GAP = 0.04;

	public record FeatureMinGaps(double low, double mid, double high) {}

	private PatternGenerator() {}

	/**
	 * 根据复杂度得到全局参考最小间隔（秒）。Low 更稀疏，Extreme 更密。
	 */
	public static double getMinGapSeconds(Complexity complexity) {
		if (complexity == null) return DEFAULT_LOW_GAP;
		return switch (complexity) {
			case LOW -> 0.25;
			case MEDIUM -> DEFAULT_LOW_GAP;
			case HIGH -> 0.06;
			case EXTREME -> DEFAULT_HIGH_GAP;
		};
	}

	/** 按频段与复杂度解析最小间隔（与 AutoMapRule 默认比例一致）。 */
	public static double getMinGapSeconds(Complexity complexity, RhythmType type) {
		return minGapForFeature(AutoMapGenerator.normalizedFeatureKey(type), featureMinGaps(complexity));
	}

	public static FeatureMinGaps featureMinGaps(Complexity complexity) {
		double scale = getMinGapSeconds(complexity) / getMinGapSeconds(Complexity.MEDIUM);
		return new FeatureMinGaps(
			DEFAULT_LOW_GAP * scale,
			DEFAULT_MID_GAP * scale,
			DEFAULT_HIGH_GAP * scale
		);
	}

	public static FeatureMinGaps featureMinGaps(AutoMapSettings settings) {
		if (settings == null) return featureMinGaps(Complexity.MEDIUM);
		FeatureMinGaps base = featureMinGaps(settings.getComplexity());
		return new FeatureMinGaps(
			settings.resolveMinGapLow(base.low()),
			settings.resolveMinGapMid(base.mid()),
			settings.resolveMinGapHigh(base.high())
		);
	}

	public static double minGapForFeature(String normalizedFeature, FeatureMinGaps gaps) {
		return switch (normalizedFeature) {
			case "low" -> gaps.low();
			case "mid" -> gaps.mid();
			case "high" -> gaps.high();
			default -> gaps.low();
		};
	}

	/**
	 * 根据复杂度得到能量阈值，低于则丢弃。Low 只保留强拍，Extreme 几乎全留。
	 */
	public static float getEnergyThreshold(Complexity complexity) {
		if (complexity == null) return 0.2f;
		return switch (complexity) {
			case LOW -> 0.5f;
			case MEDIUM -> 0.25f;
			case HIGH -> 0.15f;
			case EXTREME -> 0.05f;
		};
	}

	/** 按 per-feature 间隔与能量阈值过滤节奏事件。 */
	public static List<RhythmEvent> filter(List<RhythmEvent> events, Complexity complexity) {
		if (complexity == null) complexity = Complexity.MEDIUM;
		return filter(events, featureMinGaps(complexity), getEnergyThreshold(complexity));
	}

	/** 使用 Smart Auto Map 设置中的 per-feature minGap 过滤节奏事件。 */
	public static List<RhythmEvent> filter(List<RhythmEvent> events, AutoMapSettings settings) {
		if (settings == null) return filter(events, Complexity.MEDIUM);
		return filter(events, featureMinGaps(settings), getEnergyThreshold(settings.getComplexity()));
	}

	private static List<RhythmEvent> filter(List<RhythmEvent> events, FeatureMinGaps gaps, float minEnergy) {
		if (events == null) return List.of();
		List<RhythmEvent> sorted = new ArrayList<>(events);
		sorted.sort(Comparator.comparingDouble(RhythmEvent::getTimeSeconds));

		Map<String, Double> lastTimeByFeature = new HashMap<>();
		List<RhythmEvent> out = new ArrayList<>(sorted.size());
		for (RhythmEvent event : sorted) {
			if (event.getEnergy() < minEnergy) continue;
			String feature = AutoMapGenerator.normalizedFeatureKey(event.getType());
			double minGap = minGapForFeature(feature, gaps);
			double lastTime = lastTimeByFeature.getOrDefault(feature, -minGap - 1);
			if (event.getTimeSeconds() < lastTime + minGap) continue;
			out.add(event);
			lastTimeByFeature.put(feature, event.getTimeSeconds());
		}
		return out;
	}
}
