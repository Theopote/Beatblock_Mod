package com.beatblock.automap.choreography;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按频段对 {@link ChoreographyPlan.MotionPhrase} 应用最小时间间隔。 */
final class ChoreographyMotionGapResolver {

	private ChoreographyMotionGapResolver() {}

	static List<ChoreographyPlan.MotionPhrase> resolve(
		List<ChoreographyPlan.MotionPhrase> phrases,
		double defaultMinGapSeconds
	) {
		if (phrases == null || phrases.isEmpty()) return List.of();
		double fallbackGap = Math.max(0.0, defaultMinGapSeconds);

		List<ChoreographyPlan.MotionPhrase> sorted = new ArrayList<>(phrases);
		sorted.sort(Comparator
			.comparingDouble(ChoreographyPlan.MotionPhrase::timeSeconds)
			.thenComparing(ChoreographyPlan.MotionPhrase::energy, Comparator.reverseOrder()));

		Map<String, Double> lastTimeByFeature = new HashMap<>();
		List<ChoreographyPlan.MotionPhrase> accepted = new ArrayList<>(sorted.size());
		for (ChoreographyPlan.MotionPhrase phrase : sorted) {
			String featureKey = phrase.normalizedFeatureKey();
			double minGap = phrase.resolveMinGap(fallbackGap);
			double lastTime = lastTimeByFeature.getOrDefault(featureKey, -minGap - 1);
			if (phrase.timeSeconds() < lastTime + minGap) {
				continue;
			}
			accepted.add(phrase);
			lastTimeByFeature.put(featureKey, phrase.timeSeconds());
		}
		return accepted;
	}
}
