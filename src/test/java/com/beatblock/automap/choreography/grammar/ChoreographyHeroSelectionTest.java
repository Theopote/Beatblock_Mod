package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyHeroSelectionTest {

	@Test
	void onlyClimaxSectionsAreHeroEligible() {
		assertTrue(ChoreographyHeroSelection.isEligible(SectionType.DROP));
		assertTrue(ChoreographyHeroSelection.isEligible(SectionType.CHORUS));
		assertTrue(ChoreographyHeroSelection.isEligible(SectionType.BUILD));
		assertTrue(ChoreographyHeroSelection.isEligible(SectionType.PRE_CHORUS));
		assertFalse(ChoreographyHeroSelection.isEligible(SectionType.VERSE));
		assertFalse(ChoreographyHeroSelection.isEligible(SectionType.INTRO));
		assertFalse(ChoreographyHeroSelection.isEligible(SectionType.OUTRO));
		assertFalse(ChoreographyHeroSelection.isEligible(SectionType.BREAK));
		assertFalse(ChoreographyHeroSelection.isEligible(null));
	}

	@Test
	void dropHeroUsesFirstHighEnergyKickAndExplode() {
		ChoreographyPhrase hero = ChoreographyHeroSelection.phraseForSection(
			0,
			SectionType.DROP,
			TargetSet.of("a", "b", "c")
		);

		assertTrue(hero.isHero());
		assertEquals(ChoreographyLayer.HERO, hero.layer());
		TriggerSpec.FirstFeature trigger = assertInstanceOf(TriggerSpec.FirstFeature.class, hero.trigger());
		assertEquals("kick", trigger.normalizedFeatureKey());
		assertEquals(0.70f, trigger.minEnergy(), 1e-6f);
		assertEquals(SpatialMotifId.EXPLODE, hero.spatial().resolvedPattern());
		assertEquals("jump", hero.motion().presetId());
		assertInstanceOf(TimingPatternSpec.Simultaneous.class, hero.timing());
		assertEquals(1.0f, hero.intensity().startEnergy(), 1e-6f);
	}

	@Test
	void verseDoesNotCreateHeroPhrase() {
		assertNull(ChoreographyHeroSelection.phraseForSection(
			0,
			SectionType.VERSE,
			TargetSet.of("a", "b")
		));
	}

	@Test
	void firstFeatureResolvesOnlyFirstKickAboveEnergyInSection() {
		ChoreographyPhrase hero = ChoreographyHeroSelection.phraseForSection(
			0,
			SectionType.CHORUS,
			TargetSet.of("a", "b")
		);
		PhraseTriggerContext context = PhraseTriggerContext.forSection(
			new PhraseTriggerContext(List.of(
				new FeatureEventRef(1.0, "kick", 0.5f),
				new FeatureEventRef(2.0, "kick", 0.9f),
				new FeatureEventRef(3.0, "kick", 1.0f),
				new FeatureEventRef(12.0, "kick", 1.0f)
			)),
			new com.beatblock.automap.choreography.ChoreographyPlan.SectionPlan(0, 10, SectionType.CHORUS, "chorus")
		);

		List<TriggerInstance> triggers = PhraseTriggerResolver.resolve(hero, context);

		assertEquals(1, triggers.size());
		assertEquals(2.0, triggers.getFirst().timeSeconds(), 1e-9);
		assertEquals(0.9f, triggers.getFirst().featureEnergy(), 1e-6f);
	}
}
