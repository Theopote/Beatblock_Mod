package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGeneratorIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
		List<TimelineAnimationEvent> draft = List.of(
			tagged(1.0, "jump", "a", 1.0f, ChoreographyLayer.HERO, 0),
			tagged(1.0, "jump", "b", 1.0f, ChoreographyLayer.HERO, 0),
			tagged(4.0, "jump", "a", 0.8f, ChoreographyLayer.HERO, 0),
			tagged(4.0, "jump", "b", 0.8f, ChoreographyLayer.HERO, 0),
			tagged(8.0, "jump", "a", 0.6f, ChoreographyLayer.HERO, 0),
			tagged(8.0, "jump", "b", 0.6f, ChoreographyLayer.HERO, 0)
		);

		List<TimelineAnimationEvent> kept = ChoreographyBudgetEnforcer.enforce(draft, plan);

		long heroMoments = kept.stream()
			.filter(event -> "HERO".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.map(event -> Math.round(event.getTimeSeconds() * 20.0) / 20.0)
			.distinct()
			.count();
		assertEquals(1, heroMoments);
		assertTrue(kept.stream().allMatch(event -> Math.abs(event.getTimeSeconds() - 1.0) < 1e-9));
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
