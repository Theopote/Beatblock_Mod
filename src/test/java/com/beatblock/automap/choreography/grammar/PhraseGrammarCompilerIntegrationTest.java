package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.ReplaceMode;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class PhraseGrammarCompilerIntegrationTest {

	@Test
	void compilesChoreographyPhraseGrammarToTimelineEvents() {
		Timeline timeline = Timeline.createDefault();
		for (int i = 0; i < 9; i++) {
			timeline.addFeatureEvent("kick", new FeatureEvent(i, 0.9f));
		}

		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNBeats(4, "kick"),
			TargetSet.of("Tower_A", "Tower_B", "Tower_C", "Tower_D"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.crescendo(0.6f, 1.0f),
			VariationSpec.none(),
			0
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 16, SectionType.CHORUS, "chorus")),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(phrase)
		);

		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, ReplaceMode.APPEND);

		assertEquals(12, count);
		assertEquals("Tower_A", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("bounce", timeline.getAutoAnimationEvents().get(0).getAnimationTypeId());
		assertTrue(timeline.getAutoAnimationEvents().get(0).getParameters().containsKey("phraseGrammar"));
	}
}
