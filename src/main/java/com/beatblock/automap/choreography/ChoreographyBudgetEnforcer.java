package com.beatblock.automap.choreography;

import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在 minGap 之后对展开后的动画草稿施加 {@link ChoreographyBudget}。
 * <p>
 * 优先保留 HERO &gt; PHRASE &gt; ACCENT，并限制每拍事件数、并发对象与段内 Hero 次数。
 */
final class ChoreographyBudgetEnforcer {

	private ChoreographyBudgetEnforcer() {}

	static List<TimelineAnimationEvent> enforce(
		List<TimelineAnimationEvent> draft,
		ChoreographyPlan plan
	) {
		if (draft == null || draft.isEmpty()) return List.of();
		ChoreographyPlan resolvedPlan = plan != null ? plan : ChoreographyPlan.empty();
		List<TimelineAnimationEvent> afterHeroCap = capHeroMomentsPerSection(draft, resolvedPlan);
		return capBeatWindows(afterHeroCap, resolvedPlan);
	}

	private static List<TimelineAnimationEvent> capHeroMomentsPerSection(
		List<TimelineAnimationEvent> draft,
		ChoreographyPlan plan
	) {
		Map<Integer, List<HeroMoment>> momentsBySection = new LinkedHashMap<>();
		for (TimelineAnimationEvent event : draft) {
			if (layerOf(event) != ChoreographyLayer.HERO) continue;
			int sectionIndex = sectionIndexOf(event);
			double momentKey = Math.round(event.getTimeSeconds() * 20.0) / 20.0;
			List<HeroMoment> moments = momentsBySection.computeIfAbsent(sectionIndex, ignored -> new ArrayList<>());
			HeroMoment existing = findMoment(moments, momentKey);
			if (existing == null) {
				moments.add(new HeroMoment(momentKey, event.getEnergy(), List.of(event)));
			} else {
				existing.add(event);
			}
		}

		Set<TimelineAnimationEvent> dropped = new HashSet<>();
		for (Map.Entry<Integer, List<HeroMoment>> entry : momentsBySection.entrySet()) {
			int sectionIndex = entry.getKey();
			ChoreographyBudget budget = budgetForSection(plan, sectionIndex);
			List<HeroMoment> moments = new ArrayList<>(entry.getValue());
			moments.sort(Comparator
				.comparingDouble(HeroMoment::peakEnergy).reversed()
				.thenComparingDouble(HeroMoment::timeSeconds));
			for (int i = budget.maxHeroMomentsPerSection(); i < moments.size(); i++) {
				dropped.addAll(moments.get(i).events());
			}
		}
		if (dropped.isEmpty()) return draft;
		List<TimelineAnimationEvent> kept = new ArrayList<>(draft.size());
		for (TimelineAnimationEvent event : draft) {
			if (!dropped.contains(event)) kept.add(event);
		}
		return kept;
	}

	private static List<TimelineAnimationEvent> capBeatWindows(
		List<TimelineAnimationEvent> draft,
		ChoreographyPlan plan
	) {
		double beatSeconds = resolveBeatSeconds(plan);
		Map<Long, List<TimelineAnimationEvent>> byBeat = new LinkedHashMap<>();
		for (TimelineAnimationEvent event : draft) {
			long beat = (long) Math.floor(Math.max(0.0, event.getTimeSeconds()) / beatSeconds);
			byBeat.computeIfAbsent(beat, ignored -> new ArrayList<>()).add(event);
		}

		List<TimelineAnimationEvent> kept = new ArrayList<>(draft.size());
		for (Map.Entry<Long, List<TimelineAnimationEvent>> entry : byBeat.entrySet()) {
			double beatCenter = (entry.getKey() + 0.5) * beatSeconds;
			ChoreographyBudget budget = plan.densityCurve().budgetAt(beatCenter);
			kept.addAll(selectWithinBeat(entry.getValue(), budget));
		}
		kept.sort(Comparator
			.comparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(ChoreographyBudgetEnforcer::layerPriority, Comparator.reverseOrder())
			.thenComparing(TimelineAnimationEvent::getEnergy, Comparator.reverseOrder()));
		return kept;
	}

	private static List<TimelineAnimationEvent> selectWithinBeat(
		List<TimelineAnimationEvent> events,
		ChoreographyBudget budget
	) {
		List<TimelineAnimationEvent> ranked = new ArrayList<>(events);
		ranked.sort(Comparator
			.comparingInt(ChoreographyBudgetEnforcer::layerPriority).reversed()
			.thenComparing(TimelineAnimationEvent::getEnergy, Comparator.reverseOrder())
			.thenComparingDouble(TimelineAnimationEvent::getTimeSeconds));

		Set<ChoreographyLayer> acceptedLayers = new HashSet<>();
		Set<String> acceptedTargets = new HashSet<>();
		List<TimelineAnimationEvent> accepted = new ArrayList<>();
		for (TimelineAnimationEvent event : ranked) {
			ChoreographyLayer layer = layerOf(event);
			boolean newLayer = !acceptedLayers.contains(layer);
			if (newLayer && acceptedLayers.size() >= budget.maxPhraseLayers()) {
				continue;
			}
			String target = event.getTargetObjectId() != null ? event.getTargetObjectId() : "";
			boolean newTarget = !target.isBlank() && !acceptedTargets.contains(target);
			if (newTarget && acceptedTargets.size() >= budget.maxConcurrentStageObjects()) {
				continue;
			}
			if (accepted.size() >= budget.maxEventsPerBeat()) {
				continue;
			}
			accepted.add(event);
			acceptedLayers.add(layer);
			if (!target.isBlank()) acceptedTargets.add(target);
		}
		return accepted;
	}

	private static ChoreographyBudget budgetForSection(ChoreographyPlan plan, int sectionIndex) {
		if (sectionIndex >= 0 && sectionIndex < plan.sections().size()) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(sectionIndex);
			double mid = (section.startSeconds() + section.endSeconds()) * 0.5;
			return plan.densityCurve().budgetAt(mid);
		}
		return ChoreographyBudget.forDensity(plan.densityCurve().sampleAt(0));
	}

	private static double resolveBeatSeconds(ChoreographyPlan plan) {
		List<Double> beatTimes = plan.musicalStructure().beatTimes();
		if (beatTimes != null && beatTimes.size() >= 2) {
			double sum = 0.0;
			int count = 0;
			for (int i = 1; i < beatTimes.size(); i++) {
				double delta = beatTimes.get(i) - beatTimes.get(i - 1);
				if (delta > 1e-4 && delta < 2.0) {
					sum += delta;
					count++;
				}
			}
			if (count > 0) return sum / count;
		}
		return ChoreographyBudget.DEFAULT_BEAT_SECONDS;
	}

	private static int layerPriority(TimelineAnimationEvent event) {
		return switch (layerOf(event)) {
			case HERO -> 3;
			case PHRASE -> 2;
			case ACCENT -> 1;
		};
	}

	private static ChoreographyLayer layerOf(TimelineAnimationEvent event) {
		Object raw = event.getParameters().get(ChoreographyLayer.PARAM_KEY);
		if (raw == null) return ChoreographyLayer.ACCENT;
		try {
			return ChoreographyLayer.valueOf(String.valueOf(raw).trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return ChoreographyLayer.ACCENT;
		}
	}

	private static int sectionIndexOf(TimelineAnimationEvent event) {
		TimelineGenerationMetadata metadata = TimelineGenerationMetadata.fromParameters(event.getParameters());
		return metadata.sectionIndex();
	}

	private static HeroMoment findMoment(List<HeroMoment> moments, double timeKey) {
		for (HeroMoment moment : moments) {
			if (Math.abs(moment.timeSeconds() - timeKey) < 1e-9) return moment;
		}
		return null;
	}

	private static final class HeroMoment {
		private final double timeSeconds;
		private float peakEnergy;
		private final List<TimelineAnimationEvent> events = new ArrayList<>();

		HeroMoment(double timeSeconds, float peakEnergy, List<TimelineAnimationEvent> seed) {
			this.timeSeconds = timeSeconds;
			this.peakEnergy = peakEnergy;
			this.events.addAll(seed);
		}

		void add(TimelineAnimationEvent event) {
			events.add(event);
			peakEnergy = Math.max(peakEnergy, event.getEnergy());
		}

		double timeSeconds() {
			return timeSeconds;
		}

		float peakEnergy() {
			return peakEnergy;
		}

		List<TimelineAnimationEvent> events() {
			return events;
		}
	}
}
