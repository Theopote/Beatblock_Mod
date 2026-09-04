package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class ChoreographyLayerCompilerTest {

	@Test
	void compilesAccentAndPhraseLayersWithDifferentDefaultIntensity() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.8f));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.VERSE, "verse")),
			List.of(new ChoreographyPlan.StageRoleAssignment("low", "Tower_A")),
			List.of(new ChoreographyPlan.MotionPhrase(
				1.0, "kick", "low", 0.8f, "pulse", 0.5, true, 4f, 0.0, 0
			)),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);

		ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(1, timeline.getAutoAnimationEvents().size());
		var event = timeline.getAutoAnimationEvents().getFirst();
		assertEquals("ACCENT", event.getParameters().get(ChoreographyLayer.PARAM_KEY));
		assertEquals(0.2f, event.getEnergy(), 1e-6f);
		assertEquals(0.2f, ((Number) event.getParameters().get("energy")).floatValue(), 1e-6f);
		assertEquals(0.8f, ((Number) event.getParameters().get("height")).floatValue(), 1e-6f);
	}

	@Test
	void phraseLayerDominatesAccentWhenBothCompileForSameKick() {
		Timeline timeline = Timeline.createDefault();
		for (int i = 0; i < 4; i++) {
			timeline.addFeatureEvent("kick", new FeatureEvent(i, 1.0f));
		}

		var grammarPhrase = new com.beatblock.automap.choreography.grammar.ChoreographyPhrase(
			new com.beatblock.automap.choreography.grammar.TriggerSpec.EveryNFeatureHits("kick", 4),
			com.beatblock.automap.choreography.grammar.TargetSet.of("Tower_A", "Tower_B"),
			com.beatblock.automap.choreography.grammar.SpatialPatternSpec.leftToRight(),
			com.beatblock.automap.choreography.grammar.MotionPresetSpec.bounce(),
			com.beatblock.automap.choreography.grammar.TimingPatternSpec.stagger(0.08),
			com.beatblock.automap.choreography.grammar.IntensityEnvelope.flat(1.0f),
			com.beatblock.automap.choreography.grammar.VariationSpec.none(),
			0
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.CHORUS, "chorus")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "Tower_A"),
				new ChoreographyPlan.StageRoleAssignment("mid", "Tower_B")
			),
			List.of(new ChoreographyPlan.MotionPhrase(
				0.0, "kick", "low", 1.0f, "pulse", 0.5, true, 4f, 0.0, 0
			)),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(grammarPhrase)
		);

		ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		long accentCount = timeline.getAutoAnimationEvents().stream()
			.filter(event -> "ACCENT".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.count();
		long phraseCount = timeline.getAutoAnimationEvents().stream()
			.filter(event -> "PHRASE".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.count();

		assertEquals(1, accentCount);
		assertEquals(2, phraseCount);
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.filter(event -> "PHRASE".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.allMatch(event -> event.getEnergy() > 0.5f));
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.filter(event -> "ACCENT".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY)))
			.allMatch(event -> event.getEnergy() < 0.5f));
	}

	@Test
	void heroLayerCompilesAtFullIntensityOncePerSection() {
		Timeline timeline = Timeline.createDefault();
		for (double t : new double[] {1.0, 2.0, 3.0, 4.0}) {
			timeline.addFeatureEvent("kick", new FeatureEvent(t, 0.9f));
		}

		var hero = com.beatblock.automap.choreography.grammar.ChoreographyHeroSelection.phraseForSection(
			0,
			SectionType.DROP,
			com.beatblock.automap.choreography.grammar.TargetSet.of("Tower_A", "Tower_B")
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.DROP, "drop")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "Tower_A"),
				new ChoreographyPlan.StageRoleAssignment("mid", "Tower_B")
			),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(hero)
		);

		ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertTrue(timeline.getAutoAnimationEvents().stream().allMatch(event ->
			"HERO".equals(event.getParameters().get(ChoreographyLayer.PARAM_KEY))
		));
		assertTrue(timeline.getAutoAnimationEvents().stream().allMatch(event -> event.getEnergy() >= 0.75f));
		double firstTime = timeline.getAutoAnimationEvents().get(0).getTimeSeconds();
		assertTrue(firstTime >= 1.0 && firstTime < 2.0);
		assertTrue(timeline.getAutoAnimationEvents().stream()
			.allMatch(event -> Math.abs(event.getTimeSeconds() - firstTime) < 0.5));
	}
}
