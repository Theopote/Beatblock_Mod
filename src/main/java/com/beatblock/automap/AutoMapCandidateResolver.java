package com.beatblock.automap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将多频段候选按时间排序，并按<strong>频段</strong>（归一化 feature key）应用最小间隔。
 * <p>
 * 不同频段在同一时刻可同时保留（例如 Kick→建筑、HiHat→粒子），避免全局 {@code lastTime}
 * 导致后遍历的特征轨被整轨吞掉。每条规则可配置独立的 {@link AutoMapRule#resolveMinGap(double)}。
 */
public final class AutoMapCandidateResolver {

	private AutoMapCandidateResolver() {}

	public static List<AutoMapCandidate> resolve(List<AutoMapCandidate> candidates, double defaultMinGapSeconds) {
		if (candidates == null || candidates.isEmpty()) return List.of();
		double fallbackGap = Math.max(0.0, defaultMinGapSeconds);

		List<AutoMapCandidate> sorted = new ArrayList<>(candidates);
		sorted.sort(Comparator
			.comparingDouble(AutoMapCandidate::timeSeconds)
			.thenComparing(AutoMapCandidate::energy, Comparator.reverseOrder()));

		Map<String, Double> lastTimeByFeature = new HashMap<>();
		List<AutoMapCandidate> accepted = new ArrayList<>(sorted.size());
		for (AutoMapCandidate candidate : sorted) {
			String featureKey = candidate.normalizedFeatureKey();
			double minGap = candidate.rule().resolveMinGap(fallbackGap);
			double lastTime = lastTimeByFeature.getOrDefault(featureKey, -minGap - 1);
			if (candidate.timeSeconds() < lastTime + minGap) {
				continue;
			}
			accepted.add(candidate);
			lastTimeByFeature.put(featureKey, candidate.timeSeconds());
		}
		return accepted;
	}
}
