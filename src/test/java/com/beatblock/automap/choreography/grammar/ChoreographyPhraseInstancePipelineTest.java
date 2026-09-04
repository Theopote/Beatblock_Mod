package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChoreographyPhraseInstancePipelineTest {

	@Test
	void materializerCreatesOneInstancePerTrigger() {
		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNBeats(2),
			TargetSet.of("A", "B", "C", "D"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			0
		);
		PhraseTriggerContext context = new PhraseTriggerContext(
			List.of(),
			List.of(0.0, 0.5, 1.0, 1.5, 2.0, 2.5)
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.VERSE, "verse")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.5)
		);

		List<ChoreographyPhraseInstance> instances =
			ChoreographyPhraseInstanceMaterializer.fromGrammarPhrase(
				phrase,
				context,
				0,
				plan,
				com.beatblock.automap.choreography.TimingSnapResolver.SnapContext.from(plan)
			);

		assertEquals(3, instances.size());
		assertEquals("grammar:0:t0", instances.get(0).instanceId());
		assertEquals(ChoreographyLayer.PHRASE, instances.get(0).layer());
		assertEquals(4, instances.get(0).estimatedEventCount());
	}

	@Test
	void expandInstanceProducesTaggedEvents() {
		ChoreographyPhraseInstance instance = new ChoreographyPhraseInstance(
			"grammar:1:t0",
			"CASCADE",
			ChoreographyLayer.PHRASE,
			0,
			0,
			1.0,
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			List.of("Tower_A", "Tower_B", "Tower_C", "Tower_D"),
			0.8f,
			ChoreographyPhraseInstance.priorityFor(ChoreographyLayer.PHRASE, 0.8f),
			com.beatblock.automap.choreography.ChoreographyTimingSnap.BEAT,
			TimingPatternSpec.stagger(0.08),
			VariationSpec.none(),
			0
		);

		List<PhraseGrammarExpander.ExpandedPhraseEvent> events =
			PhraseGrammarExpander.expand(instance, null);

		assertEquals(4, events.size());
		assertEquals(1.0, events.get(0).timeSeconds(), 1e-9);
		assertEquals(1.24, events.get(3).timeSeconds(), 1e-9);
		assertEquals("grammar:1:t0", events.get(0).params().get("phraseInstanceId"));
	}
}
