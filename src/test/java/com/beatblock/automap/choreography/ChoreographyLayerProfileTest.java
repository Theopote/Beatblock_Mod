package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyLayerProfileTest {

	@Test
	void accentOnlyKeepsMotionsDropsPhraseAndHero() {
		ChoreographyPlan filtered = ChoreographyLayerProfile.ACCENT_ONLY.apply(samplePlan());

		assertEquals(1, filtered.motionPhrases().size());
		assertTrue(filtered.spatialMotifPhrases().isEmpty());
		assertTrue(filtered.choreographyPhrases().isEmpty());
		assertEquals(1, filtered.cameraPhrases().size());
	}

	@Test
	void phraseKeepsNonHeroGrammarAndSpatialDropsHero() {
		ChoreographyPlan filtered = ChoreographyLayerProfile.PHRASE.apply(samplePlan());

		assertEquals(1, filtered.motionPhrases().size());
		assertEquals(1, filtered.spatialMotifPhrases().size());
		assertEquals(1, filtered.choreographyPhrases().size());
		assertTrue(filtered.choreographyPhrases().stream().noneMatch(ChoreographyPhrase::isHero));
	}

	@Test
	void heroFullPreservesEntirePlan() {
		ChoreographyPlan plan = samplePlan();
		ChoreographyPlan filtered = ChoreographyLayerProfile.HERO_FULL.apply(plan);

		assertEquals(plan, filtered);
		assertEquals(2, filtered.choreographyPhrases().size());
		assertTrue(filtered.choreographyPhrases().stream().anyMatch(ChoreographyPhrase::isHero));
	}

	@Test
	void nullPlanBecomesEmpty() {
		ChoreographyPlan filtered = ChoreographyLayerProfile.ACCENT_ONLY.apply(null);
		assertTrue(filtered.motionPhrases().isEmpty());
		assertTrue(filtered.spatialMotifPhrases().isEmpty());
		assertTrue(filtered.choreographyPhrases().isEmpty());
		assertTrue(filtered.sections().isEmpty());
	}

	private static ChoreographyPlan samplePlan() {
		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNFeatureHits("kick", 4),
			TargetSet.of("Tower_A"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.9f),
			VariationSpec.none(),
			0,
			ChoreographyTimingSnap.BEAT,
			ChoreographyLayer.PHRASE
		);
		ChoreographyPhrase hero = new ChoreographyPhrase(
			new TriggerSpec.OnFeature("low"),
			TargetSet.of("Tower_A", "Tower_B"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			new TimingPatternSpec.Simultaneous(),
			IntensityEnvelope.flat(1.0f),
			VariationSpec.none(),
			0,
			ChoreographyTimingSnap.SECTION,
			ChoreographyLayer.HERO
		);
		SpatialMotifPhrase spatial = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.CASCADE,
			List.of("Tower_A", "Tower_B"),
			MotifAxis.X,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		return new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.DROP, "drop")),
			List.of(new ChoreographyPlan.StageRoleAssignment("low", "Tower_A")),
			List.of(new ChoreographyPlan.MotionPhrase(
				1.0, "kick", "low", 0.8f, "pulse", 0.5, true, 4f, 0.0, 0
			)),
			List.of(new ChoreographyPlan.CameraPhrase(2.0, "PAN", 0)),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(spatial),
			List.of(phrase, hero)
		);
	}
}
