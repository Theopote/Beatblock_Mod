package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanBuilder;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.test.WithBeatBlockContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ChoreographyPlanBuilderGrammarTest {

	@Test
	void rhythmAnalysisPathAddsGrammarPhrasePerSection() {
		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("low", "tower-a")
			.targetForFeature("mid", "tower-b")
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromRhythmAnalysis(
			List.of(),
			List.of(new StructuralSection(0, 16, SectionType.CHORUS, "chorus", 1.0)),
			null,
			null,
			com.beatblock.automap.engine.AutoMapStyle.EDM,
			config
		);

		assertEquals(1, plan.choreographyPhrases().size());
		assertEquals(0, plan.spatialMotifPhrases().size());
		assertInstanceOf(TriggerSpec.EveryNBeats.class, plan.choreographyPhrases().getFirst().trigger());
		assertEquals(SpatialMotifId.WAVE, plan.choreographyPhrases().getFirst().spatial().resolvedPattern());
		assertEquals(2, plan.choreographyPhrases().getFirst().targets().size());
	}

	@Test
	@WithBeatBlockContext
	void timelinePathCompilesBuilderGrammarPhrases() {
		Timeline timeline = Timeline.createDefault();
		for (int i = 0; i < 8; i++) {
			timeline.addFeatureEvent("kick", new FeatureEvent(i, 0.9f));
		}

		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("low", "tower-a")
			.targetForFeature("mid", "tower-b")
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(
			timeline,
			config,
			List.of(new StructuralSection(0, 16, SectionType.BUILD, "build", 1.0))
		);

		int count = com.beatblock.automap.choreography.ChoreographyPlanCompiler.compileAnimationEvents(
			timeline, plan, com.beatblock.automap.choreography.ReplaceMode.APPEND);

		assertEquals(1, plan.choreographyPhrases().size());
		assertEquals(4, count);
	}
}
