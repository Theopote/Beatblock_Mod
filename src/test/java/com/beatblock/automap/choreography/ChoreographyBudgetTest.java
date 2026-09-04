package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGeneratorIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyBudgetTest {

	@Test
	void sectionVisualDensityMatchesProductAnchors() {
		assertEquals(0.20, ChoreographyBudget.sectionVisualDensity(SectionType.INTRO), 1e-9);
		assertEquals(0.40, ChoreographyBudget.sectionVisualDensity(SectionType.VERSE), 1e-9);
		assertEquals(0.75, ChoreographyBudget.sectionVisualDensity(SectionType.CHORUS), 1e-9);
		assertEquals(1.00, ChoreographyBudget.sectionVisualDensity(SectionType.DROP), 1e-9);
	}

	@Test
	void forDensityScalesCapsFromIntroToDrop() {
		ChoreographyBudget intro = ChoreographyBudget.forSectionType(SectionType.INTRO);
		ChoreographyBudget drop = ChoreographyBudget.forSectionType(SectionType.DROP);

		assertTrue(intro.maxEventsPerBeat() < drop.maxEventsPerBeat());
		assertTrue(intro.maxConcurrentStageObjects() < drop.maxConcurrentStageObjects());
		assertEquals(1, intro.maxPhraseLayers());
		assertEquals(3, drop.maxPhraseLayers());
		assertEquals(0, intro.maxHeroMomentsPerSection());
		assertEquals(2, drop.maxHeroMomentsPerSection());
	}

	@Test
	void densityCurveBudgetAtUsesSampledDensity() {
		DensityCurve curve = DensityCurve.ofPoints(List.of(
			new DensityCurve.Point(0, 0.2),
			new DensityCurve.Point(10, 1.0)
		));
		assertEquals(ChoreographyBudget.forDensity(0.2), curve.budgetAt(0));
		assertEquals(ChoreographyBudget.forDensity(1.0), curve.budgetAt(10));
	}

	@Test
	void enforcerDropsAccentWhenBeatExceedsEventBudget() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.2)
		);
		List<TimelineAnimationEvent> draft = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			draft.add(tagged(0.1, "pulse", "t" + i, 0.4f, ChoreographyLayer.ACCENT, 0));
		}
		draft.add(tagged(0.1, "bounce", "hero-a", 1.0f, ChoreographyLayer.PHRASE, 0));
		draft.add(tagged(0.1, "bounce", "hero-b", 1.0f, ChoreographyLayer.PHRASE, 0));

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		assertTrue(kept.size() <= plan.densityCurve().budgetAt(0.1).maxEventsPerBeat());
		assertTrue(kept.stream().anyMatch(event ->
			"PHRASE".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY))));
		assertTrue(kept.stream().noneMatch(event ->
			"ACCENT".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			|| kept.stream().filter(event ->
				"ACCENT".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY))).count()
				<= 1);
	}

	@Test
	void enforcerCapsHeroMomentsPerSectionUsingDensityBudget() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.CHORUS, "chorus")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.75)
		);
		List<TimelineAnimationEvent> draft = new ArrayList<>();
		draft.addAll(atomicPhraseBatch("hero:0", "CASCADE", 1.0, 2, ChoreographyLayer.HERO, 0, 1.0f));
		draft.addAll(atomicPhraseBatch("hero:1", "CASCADE", 4.0, 2, ChoreographyLayer.HERO, 0, 0.8f));
		draft.addAll(atomicPhraseBatch("hero:2", "CASCADE", 8.0, 2, ChoreographyLayer.HERO, 0, 0.6f));

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		long heroMoments = kept.stream()
			.filter(event -> "HERO".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.map(event -> event.getParameters().get(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID))
			.distinct()
			.count();
		assertEquals(1, heroMoments);
		assertTrue(kept.stream().allMatch(event -> Math.abs(event.getTimeSeconds() - 1.0) < 1e-9));
		assertEquals(2, kept.size(), "winning hero moment must keep its full phrase instance");
	}

	@Test
	void enforcerTreatsStaggeredHeroPhraseAsSingleMoment() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.CHORUS, "chorus")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.75)
		);
		assertEquals(1, plan.densityCurve().budgetAt(1.0).maxHeroMomentsPerSection());

		List<TimelineAnimationEvent> draft = new ArrayList<>(staggeredAtomicPhraseBatch(
			"hero:cascade",
			"CASCADE",
			List.of(1.00, 1.08, 1.16, 1.24),
			ChoreographyLayer.HERO,
			0,
			1.0f
		));
		draft.addAll(atomicPhraseBatch("hero:other", "RADIAL_BURST", 4.0, 2, ChoreographyLayer.HERO, 0, 0.7f));

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		Set<String> keptInstances = kept.stream()
			.map(event -> String.valueOf(event.getParameters().get(
				ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID)))
			.collect(Collectors.toSet());
		assertEquals(Set.of("hero:cascade"), keptInstances,
			"staggered hero cascade must count as one moment, not four 50ms slices");
		assertEquals(4, kept.size());
		assertTrue(kept.stream().noneMatch(event -> Math.abs(event.getTimeSeconds() - 4.0) < 1e-9));
	}

	@Test
	void enforcerDropsOversizedAtomicPhraseBatchIntact() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.2)
		);
		ChoreographyBudget budget = plan.densityCurve().budgetAt(0.1);
		assertTrue(budget.maxConcurrentStageObjects() < 8);

		List<TimelineAnimationEvent> draft = atomicPhraseBatch(
			"grammar:0:t0",
			"RADIAL_BURST",
			0.1,
			8,
			ChoreographyLayer.PHRASE,
			0
		);
		draft.add(tagged(0.1, "pulse", "accent-a", 0.3f, ChoreographyLayer.ACCENT, 0));

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		assertTrue(kept.stream().noneMatch(event ->
				event.getParameters().containsKey(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID)
					&& String.valueOf(event.getParameters().get(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID))
						.startsWith("grammar:0:")),
			"oversized radial batch must be dropped whole, not truncated");
		assertEquals(0, kept.stream()
			.filter(event -> "grammar:0:t0".equals(
				event.getParameters().get(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID)))
			.count());
		assertTrue(kept.stream().anyMatch(event -> "accent-a".equals(event.getTargetObjectId())));
	}

	@Test
	void enforcerKeepsAtomicPhraseBatchIntactWhenBudgetAllows() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.DROP, "drop")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		ChoreographyBudget budget = plan.densityCurve().budgetAt(0.1);
		assertTrue(budget.maxConcurrentStageObjects() >= 8);
		assertTrue(budget.maxEventsPerBeat() >= 8);

		List<TimelineAnimationEvent> draft = atomicPhraseBatch(
			"grammar:1:t0",
			"RADIAL_BURST",
			0.1,
			8,
			ChoreographyLayer.PHRASE,
			0
		);
		for (int i = 0; i < 4; i++) {
			draft.add(tagged(0.1, "pulse", "noise-" + i, 0.2f, ChoreographyLayer.ACCENT, 0));
		}

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		List<TimelineAnimationEvent> radial = kept.stream()
			.filter(event -> "grammar:1:t0".equals(
				event.getParameters().get(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID)))
			.toList();
		assertEquals(8, radial.size(), "radial burst must stay complete when budget fits");
		Set<String> radialTargets = radial.stream()
			.map(TimelineAnimationEvent::getTargetObjectId)
			.collect(Collectors.toSet());
		assertEquals(8, radialTargets.size());
	}

	@Test
	void enforcerCapsConcurrentPhraseInstancesNotEnumTypes() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.DROP, "drop")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		assertEquals(3, plan.densityCurve().budgetAt(0.1).maxPhraseLayers());

		List<TimelineAnimationEvent> draft = new ArrayList<>();
		draft.addAll(atomicPhraseBatch("grammar:0:t0", "WAVE", 0.1, 1, ChoreographyLayer.PHRASE, 0));
		draft.addAll(atomicPhraseBatch("grammar:1:t0", "RADIAL_BURST", 0.1, 1, ChoreographyLayer.PHRASE, 0));
		draft.addAll(atomicPhraseBatch("grammar:2:t0", "ALTERNATE", 0.1, 1, ChoreographyLayer.PHRASE, 0));
		draft.addAll(atomicPhraseBatch("grammar:3:t0", "CASCADE", 0.1, 1, ChoreographyLayer.PHRASE, 0));
		draft.add(tagged(0.1, "pulse", "accent-a", 0.2f, ChoreographyLayer.ACCENT, 0));

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		long phraseInstances = kept.stream()
			.filter(event -> event.getParameters().containsKey(
				ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID))
			.map(event -> event.getParameters().get(ChoreographyPhraseBatchSupport.PARAM_PHRASE_INSTANCE_ID))
			.distinct()
			.count();
		assertEquals(3, phraseInstances,
			"maxPhraseLayers must cap parallel Phrase instances, not ACCENT/PHRASE/HERO enum kinds");
		assertTrue(kept.stream().anyMatch(event -> "accent-a".equals(event.getTargetObjectId())),
			"Accent must not consume a phrase-instance slot");
	}

	@Test
	void enforcerGroupsByRealBeatGridNotAveragedOrigin() {
		// Real grid offset from 0: [0.137, 0.637) vs [0.637, 1.137)
		// Averaged 0.5s windows from t=0 would put 0.60 and 0.65 in the SAME bucket.
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(
				List.of(),
				List.of(),
				List.of(),
				List.of(0.137, 0.637, 1.137, 1.637)
			)
		);
		assertEquals(1, plan.densityCurve().budgetAt(0.6).maxEventsPerBeat());

		List<TimelineAnimationEvent> draft = List.of(
			tagged(0.60, "pulse", "a", 1.0f, ChoreographyLayer.ACCENT, 0),
			tagged(0.65, "pulse", "b", 1.0f, ChoreographyLayer.ACCENT, 0)
		);

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		assertEquals(2, kept.size(),
			"events in adjacent real beats must not share one averaged-origin budget window");
		assertTrue(kept.stream().anyMatch(event -> "a".equals(event.getTargetObjectId())));
		assertTrue(kept.stream().anyMatch(event -> "b".equals(event.getTargetObjectId())));
	}

	@Test
	void enforcerRespectsLocalBeatDurationAcrossTempoChange() {
		// 100 BPM (~0.6s) then 140 BPM (~0.429s). Average duration mis-buckets the drop hits.
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.DROP, "drop")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.0),
			List.of(),
			new ChoreographyPlan.MusicalStructure(
				List.of(),
				List.of(),
				List.of(),
				List.of(0.0, 0.6, 1.2, 1.629, 2.058)
			)
		);
		assertEquals(1, plan.densityCurve().budgetAt(1.4).maxEventsPerBeat());

		List<TimelineAnimationEvent> draft = List.of(
			tagged(1.30, "pulse", "drop-a", 1.0f, ChoreographyLayer.ACCENT, 0),
			tagged(1.75, "pulse", "drop-b", 1.0f, ChoreographyLayer.ACCENT, 0)
		);

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		assertEquals(2, kept.size(),
			"tempo-changed local beats must each get their own budget window");
	}

	@Test
	void enforceInstancesDropsOversizedPhraseBeforeExpand() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.2)
		);
		assertTrue(plan.densityCurve().budgetAt(0.1).maxConcurrentStageObjects() < 8);

		List<com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance> instances = new ArrayList<>();
		instances.add(new com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance(
			"grammar:0:t0",
			"EXPLODE",
			ChoreographyLayer.PHRASE,
			0,
			0,
			0.1,
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.of(
				SpatialMotifId.EXPLODE,
				MotifAxis.RADIAL
			),
			com.beatblock.automap.choreography.grammar.MotionPresetSpec.bounce(),
			List.of("a", "b", "c", "d", "e", "f", "g", "h"),
			0.9f,
			com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance.priorityFor(
				ChoreographyLayer.PHRASE, 0.9f),
			ChoreographyTimingSnap.BEAT,
			new com.beatblock.automap.choreography.grammar.TimingPatternSpec.Simultaneous(),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			0
		));
		instances.add(com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance.accent(
			"accent:0",
			"kick",
			0,
			0,
			0.1,
			"accent-a",
			com.beatblock.automap.choreography.grammar.MotionPresetSpec.of("pulse"),
			0.3f,
			ChoreographyTimingSnap.BEAT
		));

		List<com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance> kept =
			ChoreographyBudgetEnforcer.enforceInstances(instances, plan);

		assertEquals(1, kept.size());
		assertEquals("accent:0", kept.get(0).instanceId());
	}

	private static List<TimelineAnimationEvent> atomicPhraseBatch(
		String instanceId,
		String phraseId,
		double time,
		int targetCount,
		ChoreographyLayer layer,
		int sectionIndex
	) {
		return atomicPhraseBatch(instanceId, phraseId, time, targetCount, layer, sectionIndex, 0.9f);
	}

	private static List<TimelineAnimationEvent> atomicPhraseBatch(
		String instanceId,
		String phraseId,
		double time,
		int targetCount,
		ChoreographyLayer layer,
		int sectionIndex,
		float energy
	) {
		List<Double> times = new ArrayList<>(targetCount);
		for (int i = 0; i < targetCount; i++) {
			times.add(time);
		}
		return staggeredAtomicPhraseBatch(instanceId, phraseId, times, layer, sectionIndex, energy);
	}

	private static List<TimelineAnimationEvent> staggeredAtomicPhraseBatch(
		String instanceId,
		String phraseId,
		List<Double> times,
		ChoreographyLayer layer,
		int sectionIndex,
		float energy
	) {
		int targetCount = times.size();
		List<TimelineAnimationEvent> events = new ArrayList<>(targetCount);
		for (int i = 0; i < targetCount; i++) {
			TimelineAnimationEvent base = tagged(
				times.get(i),
				"bounce",
				instanceId.replace(':', '-') + "-" + i,
				energy,
				layer,
				sectionIndex
			);
			Map<String, Object> params = ChoreographyPhraseBatchSupport.tagAtomicBatch(
				new HashMap<>(base.getParameters()),
				instanceId,
				phraseId,
				targetCount
			);
			events.add(new TimelineAnimationEvent(
				base.getEventId(),
				base.getTimeSeconds(),
				base.getDurationSeconds(),
				base.getAnimationTypeId(),
				base.getTargetObjectId(),
				base.getEnergy(),
				params
			));
		}
		return events;
	}

	private static TimelineAnimationEvent tagged(
		double time,
		String animation,
		String target,
		float energy,
		ChoreographyLayer layer,
		int sectionIndex
	) {
		TimelineAnimationEvent raw = new TimelineAnimationEvent(
			"", time, 0.5, animation, target, energy, Map.of("energy", energy));
		TimelineGenerationMetadata metadata = new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			TimelineGeneratorIds.SMART_AUTOMAP,
			"gen-budget",
			sectionIndex,
			-1,
			""
		);
		return TimelineDraftWriter.withMetadata(
			new TimelineAnimationEvent(
				raw.getEventId(),
				raw.getTimeSeconds(),
				raw.getDurationSeconds(),
				raw.getAnimationTypeId(),
				raw.getTargetObjectId(),
				layer.scaleEnergy(raw.getEnergy()),
				layer.scaleEventParams(raw.getParameters(), raw.getEnergy())
			),
			metadata
		);
	}
}
