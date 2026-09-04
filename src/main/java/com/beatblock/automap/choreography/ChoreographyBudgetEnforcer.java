package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对编舞草稿施加 {@link ChoreographyBudget}。
 * <p>
 * 首选在 {@link ChoreographyPhraseInstance} 层裁剪（Expand 之前），整组保留或丢弃；
 * 亦保留对已展开事件的路径，供回归与过渡期使用。
 * 拍窗优先按真实 {@link ChoreographyPlan.MusicalStructure#beatTimes()} 划分。
 */
final class ChoreographyBudgetEnforcer {

	private ChoreographyBudgetEnforcer() {}

	/** Phrase Grammar v1：在 Expand 之前按 Instance 裁剪。 */
	static List<ChoreographyPhraseInstance> enforceInstances(
		List<ChoreographyPhraseInstance> instances,
		ChoreographyPlan plan
	) {
		if (instances == null || instances.isEmpty()) return List.of();
		ChoreographyPlan resolvedPlan = plan != null ? plan : ChoreographyPlan.empty();
		List<ChoreographyPhraseInstance> afterHeroCap = capHeroMomentsOnInstances(instances, resolvedPlan);
		return capBeatWindowsOnInstances(afterHeroCap, resolvedPlan);
	}

	/** 过渡期：已展开事件路径；新编译走 {@link #enforceInstances}。 */
	@Deprecated
	static List<TimelineAnimationEvent> enforce(
		List<TimelineAnimationEvent> draft,
		ChoreographyPlan plan
	) {
		if (draft == null || draft.isEmpty()) return List.of();
		ChoreographyPlan resolvedPlan = plan != null ? plan : ChoreographyPlan.empty();
		List<ExpandedPhraseBatch> batches = groupIntoBatches(draft);
		List<ExpandedPhraseBatch> afterHeroCap = capHeroMomentsPerSection(batches, resolvedPlan);
		return capBeatWindows(afterHeroCap, resolvedPlan);
	}

	private static List<ChoreographyPhraseInstance> capHeroMomentsOnInstances(
		List<ChoreographyPhraseInstance> instances,
		ChoreographyPlan plan
	) {
		Map<Integer, List<ChoreographyPhraseInstance>> heroesBySection = new LinkedHashMap<>();
		for (ChoreographyPhraseInstance instance : instances) {
			if (!instance.isHero()) continue;
			heroesBySection
				.computeIfAbsent(instance.sectionIndex(), ignored -> new ArrayList<>())
				.add(instance);
		}

		Set<String> droppedIds = new HashSet<>();
		for (Map.Entry<Integer, List<ChoreographyPhraseInstance>> entry : heroesBySection.entrySet()) {
			ChoreographyBudget budget = budgetForSection(plan, entry.getKey());
			List<ChoreographyPhraseInstance> heroes = new ArrayList<>(entry.getValue());
			heroes.sort(Comparator
				.comparingDouble(ChoreographyPhraseInstance::priority).reversed()
				.thenComparingDouble(ChoreographyPhraseInstance::triggerTime)
				.thenComparing(ChoreographyPhraseInstance::instanceId));
			for (int i = budget.maxHeroMomentsPerSection(); i < heroes.size(); i++) {
				droppedIds.add(heroes.get(i).instanceId());
			}
		}
		if (droppedIds.isEmpty()) return instances;
		List<ChoreographyPhraseInstance> kept = new ArrayList<>(instances.size());
		for (ChoreographyPhraseInstance instance : instances) {
			if (!droppedIds.contains(instance.instanceId())) kept.add(instance);
		}
		return kept;
	}

	private static List<ChoreographyPhraseInstance> capBeatWindowsOnInstances(
		List<ChoreographyPhraseInstance> instances,
		ChoreographyPlan plan
	) {
		List<BeatSpan> spans = resolveBeatSpans(plan);
		Map<Integer, List<ChoreographyPhraseInstance>> byBeat = new LinkedHashMap<>();
		Map<Integer, BeatSpan> spanByIndex = new LinkedHashMap<>();
		for (BeatSpan span : spans) {
			spanByIndex.put(span.index(), span);
		}
		for (ChoreographyPhraseInstance instance : instances) {
			BeatSpan span = findBeatSpan(spans, instance.triggerTime());
			byBeat.computeIfAbsent(span.index(), ignored -> new ArrayList<>()).add(instance);
			spanByIndex.putIfAbsent(span.index(), span);
		}

		List<ChoreographyPhraseInstance> kept = new ArrayList<>();
		for (Map.Entry<Integer, List<ChoreographyPhraseInstance>> entry : byBeat.entrySet()) {
			BeatSpan span = spanByIndex.get(entry.getKey());
			double budgetTime = span != null ? span.centerSeconds() : 0.0;
			ChoreographyBudget budget = plan.densityCurve().budgetAt(budgetTime);
			kept.addAll(selectInstancesWithinBeat(entry.getValue(), budget));
		}
		kept.sort(Comparator
			.comparingDouble(ChoreographyPhraseInstance::triggerTime)
			.thenComparing(ChoreographyPhraseInstance::priority, Comparator.reverseOrder())
			.thenComparing(ChoreographyPhraseInstance::instanceId));
		return kept;
	}

	private static List<ChoreographyPhraseInstance> selectInstancesWithinBeat(
		List<ChoreographyPhraseInstance> instances,
		ChoreographyBudget budget
	) {
		List<ChoreographyPhraseInstance> ranked = new ArrayList<>(instances);
		ranked.sort(Comparator
			.comparingDouble(ChoreographyPhraseInstance::priority).reversed()
			.thenComparingDouble(ChoreographyPhraseInstance::triggerTime)
			.thenComparing(ChoreographyPhraseInstance::instanceId));

		Set<String> activePhraseInstances = new HashSet<>();
		Set<String> acceptedTargets = new HashSet<>();
		int acceptedEventCount = 0;
		List<ChoreographyPhraseInstance> accepted = new ArrayList<>();

		for (ChoreographyPhraseInstance instance : ranked) {
			if (instance.countsTowardPhraseLayerBudget()
				&& !activePhraseInstances.contains(instance.instanceId())
				&& activePhraseInstances.size() >= budget.maxPhraseLayers()) {
				continue;
			}

			List<String> targets = instance.targetIds();
			int addedTargets = 0;
			for (String target : targets) {
				if (target != null && !target.isBlank() && !acceptedTargets.contains(target)) {
					addedTargets++;
				}
			}
			if (acceptedTargets.size() + addedTargets > budget.maxConcurrentStageObjects()) {
				continue;
			}

			int eventCount = instance.estimatedEventCount();
			if (acceptedEventCount + eventCount > budget.maxEventsPerBeat()) {
				continue;
			}

			accepted.add(instance);
			if (instance.countsTowardPhraseLayerBudget()) {
				activePhraseInstances.add(instance.instanceId());
			}
			for (String target : targets) {
				if (target != null && !target.isBlank()) acceptedTargets.add(target);
			}
			acceptedEventCount += eventCount;
		}
		return accepted;
	}

	private static List<ExpandedPhraseBatch> groupIntoBatches(List<TimelineAnimationEvent> draft) {
		Map<String, List<TimelineAnimationEvent>> atomicGroups = new LinkedHashMap<>();
		List<TimelineAnimationEvent> singletons = new ArrayList<>();
		int singletonOrdinal = 0;
		for (TimelineAnimationEvent event : draft) {
			String instanceId = ChoreographyPhraseBatchSupport.phraseInstanceIdOf(event);
			if (ChoreographyPhraseBatchSupport.isAtomicBatch(event) && !instanceId.isEmpty()) {
				atomicGroups.computeIfAbsent(instanceId, ignored -> new ArrayList<>()).add(event);
			} else {
				singletons.add(event);
			}
		}

		List<ExpandedPhraseBatch> batches = new ArrayList<>(atomicGroups.size() + singletons.size());
		for (Map.Entry<String, List<TimelineAnimationEvent>> entry : atomicGroups.entrySet()) {
			batches.add(ExpandedPhraseBatch.fromEvents(entry.getKey(), entry.getValue(), true));
		}
		for (TimelineAnimationEvent event : singletons) {
			batches.add(ExpandedPhraseBatch.fromEvents(
				"singleton:" + singletonOrdinal++,
				List.of(event),
				false
			));
		}
		return batches;
	}

	private static List<ExpandedPhraseBatch> capHeroMomentsPerSection(
		List<ExpandedPhraseBatch> batches,
		ChoreographyPlan plan
	) {
		// 每个 HERO ExpandedPhraseBatch 已是一个 Phrase Instance（含 stagger 全组），
		// 不再按 50ms 时间窗二次聚类，避免把 Cascade 拆成多个 Hero Moment。
		Map<Integer, List<HeroMoment>> momentsBySection = new LinkedHashMap<>();
		for (ExpandedPhraseBatch batch : batches) {
			if (batch.layer() != ChoreographyLayer.HERO) continue;
			int sectionIndex = batch.sectionIndex();
			momentsBySection
				.computeIfAbsent(sectionIndex, ignored -> new ArrayList<>())
				.add(new HeroMoment(batch.phraseId(), batch.triggerTime(), batch.priority(), List.of(batch)));
		}

		Set<ExpandedPhraseBatch> dropped = new HashSet<>();
		for (Map.Entry<Integer, List<HeroMoment>> entry : momentsBySection.entrySet()) {
			int sectionIndex = entry.getKey();
			ChoreographyBudget budget = budgetForSection(plan, sectionIndex);
			List<HeroMoment> moments = new ArrayList<>(entry.getValue());
			moments.sort(Comparator
				.comparingDouble(HeroMoment::peakEnergy).reversed()
				.thenComparingDouble(HeroMoment::timeSeconds)
				.thenComparing(HeroMoment::instanceId));
			for (int i = budget.maxHeroMomentsPerSection(); i < moments.size(); i++) {
				dropped.addAll(moments.get(i).batches());
			}
		}
		if (dropped.isEmpty()) return batches;
		List<ExpandedPhraseBatch> kept = new ArrayList<>(batches.size());
		for (ExpandedPhraseBatch batch : batches) {
			if (!dropped.contains(batch)) kept.add(batch);
		}
		return kept;
	}

	private static List<TimelineAnimationEvent> capBeatWindows(
		List<ExpandedPhraseBatch> batches,
		ChoreographyPlan plan
	) {
		List<BeatSpan> spans = resolveBeatSpans(plan);
		Map<Integer, List<ExpandedPhraseBatch>> byBeat = new LinkedHashMap<>();
		Map<Integer, BeatSpan> spanByIndex = new LinkedHashMap<>();
		for (BeatSpan span : spans) {
			spanByIndex.put(span.index(), span);
		}

		for (ExpandedPhraseBatch batch : batches) {
			BeatSpan span = findBeatSpan(spans, batch.triggerTime());
			byBeat.computeIfAbsent(span.index(), ignored -> new ArrayList<>()).add(batch);
			spanByIndex.putIfAbsent(span.index(), span);
		}

		List<TimelineAnimationEvent> kept = new ArrayList<>();
		for (Map.Entry<Integer, List<ExpandedPhraseBatch>> entry : byBeat.entrySet()) {
			BeatSpan span = spanByIndex.get(entry.getKey());
			double budgetTime = span != null ? span.centerSeconds() : 0.0;
			ChoreographyBudget budget = plan.densityCurve().budgetAt(budgetTime);
			for (ExpandedPhraseBatch batch : selectWithinBeat(entry.getValue(), budget)) {
				kept.addAll(batch.events());
			}
		}
		kept.sort(Comparator
			.comparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(ChoreographyBudgetEnforcer::layerPriority, Comparator.reverseOrder())
			.thenComparing(TimelineAnimationEvent::getEnergy, Comparator.reverseOrder()));
		return kept;
	}

	private static List<ExpandedPhraseBatch> selectWithinBeat(
		List<ExpandedPhraseBatch> batches,
		ChoreographyBudget budget
	) {
		List<ExpandedPhraseBatch> ranked = new ArrayList<>(batches);
		ranked.sort(Comparator
			.comparingInt(ExpandedPhraseBatch::layerPriority).reversed()
			.thenComparing(ExpandedPhraseBatch::priority, Comparator.reverseOrder())
			.thenComparingDouble(ExpandedPhraseBatch::triggerTime));

		Set<String> activePhraseInstances = new HashSet<>();
		Set<String> acceptedTargets = new HashSet<>();
		int acceptedEventCount = 0;
		List<ExpandedPhraseBatch> accepted = new ArrayList<>();

		for (ExpandedPhraseBatch batch : ranked) {
			PhraseBudgetKey phraseKey = batch.phraseBudgetKey();
			if (phraseKey != null
				&& !activePhraseInstances.contains(phraseKey.instanceId())
				&& activePhraseInstances.size() >= budget.maxPhraseLayers()) {
				continue;
			}

			Set<String> batchTargets = batch.targets();
			int addedTargets = 0;
			for (String target : batchTargets) {
				if (!acceptedTargets.contains(target)) addedTargets++;
			}
			if (acceptedTargets.size() + addedTargets > budget.maxConcurrentStageObjects()) {
				continue;
			}

			int batchEventCount = batch.events().size();
			if (acceptedEventCount + batchEventCount > budget.maxEventsPerBeat()) {
				continue;
			}

			accepted.add(batch);
			if (phraseKey != null) {
				activePhraseInstances.add(phraseKey.instanceId());
			}
			acceptedTargets.addAll(batchTargets);
			acceptedEventCount += batchEventCount;
		}
		return accepted;
	}

	/**
	 * 优先使用 {@link ChoreographyPlan.MusicalStructure#beatTimes()} 构建真实拍窗
	 * {@code [beat_i, beat_{i+1})}；网格为空时按 {@link ChoreographyBudget#DEFAULT_BEAT_SECONDS} 从 0 起合成。
	 */
	private static List<BeatSpan> resolveBeatSpans(ChoreographyPlan plan) {
		List<Double> rawBeats = plan.musicalStructure().beatTimes();
		if (rawBeats != null && !rawBeats.isEmpty()) {
			List<Double> beats = sanitizeBeatTimes(rawBeats);
			if (!beats.isEmpty()) {
				return buildSpansFromBeatTimes(beats);
			}
		}
		return List.of();
	}

	private static List<Double> sanitizeBeatTimes(List<Double> rawBeats) {
		List<Double> beats = new ArrayList<>(rawBeats.size());
		double previous = Double.NEGATIVE_INFINITY;
		for (Double raw : rawBeats) {
			if (raw == null || !Double.isFinite(raw)) continue;
			double time = raw;
			if (time < previous + 1e-6) continue;
			beats.add(time);
			previous = time;
		}
		return beats;
	}

	private static List<BeatSpan> buildSpansFromBeatTimes(List<Double> beats) {
		List<BeatSpan> spans = new ArrayList<>(beats.size() + 1);
		double first = beats.getFirst();
		if (first > 1e-6) {
			spans.add(BeatSpan.closed(-1, 0.0, first));
		}
		for (int i = 0; i < beats.size(); i++) {
			double start = beats.get(i);
			if (i + 1 < beats.size()) {
				spans.add(BeatSpan.closed(i, start, beats.get(i + 1)));
			} else {
				double lastDelta = i > 0
					? beats.get(i) - beats.get(i - 1)
					: ChoreographyBudget.DEFAULT_BEAT_SECONDS;
				spans.add(BeatSpan.openEnded(i, start, Math.max(lastDelta, 1e-4)));
			}
		}
		return List.copyOf(spans);
	}

	private static BeatSpan findBeatSpan(List<BeatSpan> spans, double timeSeconds) {
		if (spans == null || spans.isEmpty()) {
			return syntheticSpan(timeSeconds);
		}
		double time = Math.max(0.0, timeSeconds);
		int index = 0;
		for (int i = 0; i < spans.size(); i++) {
			if (time >= spans.get(i).startSeconds()) {
				index = i;
			} else {
				break;
			}
		}
		return spans.get(index);
	}

	/** beatTimes 为空时：按固定拍长从 0 起划分。 */
	private static BeatSpan syntheticSpan(double timeSeconds) {
		double beatSeconds = ChoreographyBudget.DEFAULT_BEAT_SECONDS;
		long beat = (long) Math.floor(Math.max(0.0, timeSeconds) / beatSeconds);
		double start = beat * beatSeconds;
		return BeatSpan.closed((int) beat, start, start + beatSeconds);
	}

	private static ChoreographyBudget budgetForSection(ChoreographyPlan plan, int sectionIndex) {
		if (sectionIndex >= 0 && sectionIndex < plan.sections().size()) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(sectionIndex);
			double mid = (section.startSeconds() + section.endSeconds()) * 0.5;
			return plan.densityCurve().budgetAt(mid);
		}
		return ChoreographyBudget.forDensity(plan.densityCurve().sampleAt(0));
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

	/**
	 * 一次 Phrase 触发展开出的事件组。atomic=true 时预算整组接受或丢弃。
	 */
	private record ExpandedPhraseBatch(
		ChoreographyLayer layer,
		String phraseId,
		double triggerTime,
		List<TimelineAnimationEvent> events,
		float priority,
		boolean atomic,
		Set<String> targets,
		int sectionIndex
	) {
		static ExpandedPhraseBatch fromEvents(
			String phraseId,
			List<TimelineAnimationEvent> events,
			boolean atomic
		) {
			List<TimelineAnimationEvent> copy = List.copyOf(events);
			ChoreographyLayer layer = ChoreographyLayer.ACCENT;
			float peakEnergy = 0f;
			double triggerTime = Double.POSITIVE_INFINITY;
			int sectionIndex = -1;
			Set<String> targets = new HashSet<>();
			for (TimelineAnimationEvent event : copy) {
				layer = maxLayer(layer, layerOf(event));
				peakEnergy = Math.max(peakEnergy, event.getEnergy());
				triggerTime = Math.min(triggerTime, event.getTimeSeconds());
				if (sectionIndex < 0) {
					sectionIndex = TimelineGenerationMetadata.fromParameters(event.getParameters()).sectionIndex();
				}
				String target = event.getTargetObjectId();
				if (!target.isBlank()) {
					targets.add(target);
				}
			}
			if (!Double.isFinite(triggerTime)) triggerTime = 0.0;
			return new ExpandedPhraseBatch(
				layer,
				phraseId != null ? phraseId : "",
				triggerTime,
				copy,
				peakEnergy,
				atomic,
				Set.copyOf(targets),
				sectionIndex
			);
		}

		int layerPriority() {
			return switch (layer) {
				case HERO -> 3;
				case PHRASE -> 2;
				case ACCENT -> 1;
			};
		}

		/**
		 * Phrase/Hero instance 才占用 {@link ChoreographyBudget#maxPhraseLayers()}；
		 * Accent 只受事件数 / 并发对象约束。
		 */
		@Nullable PhraseBudgetKey phraseBudgetKey() {
			if (layer == ChoreographyLayer.ACCENT) return null;
			return new PhraseBudgetKey(layer, phraseId, triggerTime);
		}

		private static ChoreographyLayer maxLayer(ChoreographyLayer a, ChoreographyLayer b) {
			return a.ordinal() >= b.ordinal() ? a : b;
		}
	}

	/** 并行 Phrase/Hero instance 的预算键（不是 {@link ChoreographyLayer} 枚举种类）。 */
	private record PhraseBudgetKey(
		ChoreographyLayer layer,
		String instanceId,
		double triggerTime
	) {
		PhraseBudgetKey {
			instanceId = instanceId != null ? instanceId : "";
		}
	}

	/**
	 * 真实拍网格上的半开区间 {@code [start, end)}；末拍可为 open-ended。
	 * {@link #centerSeconds()} 用于采样 {@link DensityCurve}。
	 */
	private record BeatSpan(
		int index,
		double startSeconds,
		double endSeconds,
		double centerSeconds
	) {
		static BeatSpan closed(int index, double startSeconds, double endSeconds) {
			double start = startSeconds;
			double end = Math.max(startSeconds, endSeconds);
			return new BeatSpan(index, start, end, (start + end) * 0.5);
		}

		static BeatSpan openEnded(int index, double startSeconds, double nominalDurationSeconds) {
			double start = startSeconds;
			double duration = Math.max(1e-4, nominalDurationSeconds);
			return new BeatSpan(index, start, Double.POSITIVE_INFINITY, start + duration * 0.5);
		}
	}

	private static final class HeroMoment {
		private final String instanceId;
		private final double timeSeconds;
		private final float peakEnergy;
		private final List<ExpandedPhraseBatch> batches;

		HeroMoment(
			String instanceId,
			double timeSeconds,
			float peakEnergy,
			List<ExpandedPhraseBatch> seed
		) {
			this.instanceId = instanceId != null ? instanceId : "";
			this.timeSeconds = timeSeconds;
			this.peakEnergy = peakEnergy;
			this.batches = new ArrayList<>(seed);
		}

		String instanceId() {
			return instanceId;
		}

		double timeSeconds() {
			return timeSeconds;
		}

		float peakEnergy() {
			return peakEnergy;
		}

		List<ExpandedPhraseBatch> batches() {
			return batches;
		}
	}
}
