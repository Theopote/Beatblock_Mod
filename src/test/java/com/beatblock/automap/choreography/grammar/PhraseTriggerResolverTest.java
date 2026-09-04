package com.beatblock.automap.choreography.grammar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhraseTriggerResolverTest {

	@Test
	void everyNBeatsSelectsEveryFourthKick() {
		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNBeats(4, "kick"),
			TargetSet.of("a", "b"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			0
		);
		PhraseTriggerContext context = new PhraseTriggerContext(List.of(
			new FeatureEventRef(0.0, "kick", 0.9f),
			new FeatureEventRef(1.0, "kick", 0.8f),
			new FeatureEventRef(2.0, "kick", 0.7f),
			new FeatureEventRef(3.0, "kick", 0.6f),
			new FeatureEventRef(4.0, "kick", 0.9f),
			new FeatureEventRef(5.0, "kick", 0.8f),
			new FeatureEventRef(6.0, "kick", 0.7f),
			new FeatureEventRef(7.0, "kick", 0.6f),
			new FeatureEventRef(8.0, "kick", 1.0f)
		));

		List<TriggerInstance> triggers = PhraseTriggerResolver.resolve(phrase, context);

		assertEquals(3, triggers.size());
		assertEquals(0.0, triggers.get(0).timeSeconds(), 1e-9);
		assertEquals(4.0, triggers.get(1).timeSeconds(), 1e-9);
		assertEquals(8.0, triggers.get(2).timeSeconds(), 1e-9);
	}

	@Test
	void sectionScopedContextOnlyMatchesEventsInSectionTimeRange() {
		ChoreographyPhrase phraseA = new ChoreographyPhrase(
			new TriggerSpec.OnFeature("kick"),
			TargetSet.of("a"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			0
		);
		ChoreographyPhrase phraseB = new ChoreographyPhrase(
			new TriggerSpec.OnFeature("kick"),
			TargetSet.of("b"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			1
		);
		PhraseTriggerContext global = new PhraseTriggerContext(List.of(
			new FeatureEventRef(2.0, "kick", 0.9f),
			new FeatureEventRef(4.0, "kick", 0.8f),
			new FeatureEventRef(12.0, "kick", 0.7f),
			new FeatureEventRef(14.0, "kick", 0.6f)
		));
		PhraseTriggerContext sectionA = PhraseTriggerContext.forSection(
			global,
			new com.beatblock.automap.choreography.ChoreographyPlan.SectionPlan(0, 10, null, "A")
		);
		PhraseTriggerContext sectionB = PhraseTriggerContext.forSection(
			global,
			new com.beatblock.automap.choreography.ChoreographyPlan.SectionPlan(10, 20, null, "B")
		);

		List<TriggerInstance> triggersA = PhraseTriggerResolver.resolve(phraseA, sectionA);
		List<TriggerInstance> triggersB = PhraseTriggerResolver.resolve(phraseB, sectionB);

		assertEquals(List.of(2.0, 4.0), triggersA.stream().map(TriggerInstance::timeSeconds).toList());
		assertEquals(List.of(12.0, 14.0), triggersB.stream().map(TriggerInstance::timeSeconds).toList());
	}
}
