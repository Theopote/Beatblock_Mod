package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.test.WithBeatBlockContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithBeatBlockContext
class ChoreographyPlanCompilerTest {

	@Test
	void compilesPerFeatureTargets() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.6f));
		timeline.addFeatureEvent("snare", new FeatureEvent(1.1, 0.5f));

		AutoMapConfig config = AutoMapConfig.builder()
			.minGapSeconds(0.08)
			.rule(new AutoMapRule("low", 0.15f, "bounce", 0.5, true, 4f, 0.12, null))
			.rule(new AutoMapRule("mid", 0.2f, "slide", 0.4, true, 3f, 0.08, null))
			.rule(new AutoMapRule("high", 0.15f, "pulse", 0.3, false, 1f, 0.04, null))
			.targetForFeature("low", "stage-kick")
			.targetForFeature("mid", "stage-snare")
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(timeline, config);
		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, config, false);

		assertEquals(2, count);
		assertEquals("stage-kick", timeline.getAutoAnimationEvents().get(0).getTargetObjectId());
		assertEquals("stage-snare", timeline.getAutoAnimationEvents().get(1).getTargetObjectId());
	}

	@Test
	void skipsMotionPhrasesInVeryLowDensitySections() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("low", new FeatureEvent(2.0, 0.9f));

		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 10, com.beatblock.automap.engine.SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(
				2.0, "low", "low", 0.9f, "bounce", 0.5, true, 4f)),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.1)
		);

		int count = ChoreographyPlanCompiler.compileAnimationEvents(
			timeline, plan, AutoMapConfig.createDefault(), false);

		assertEquals(0, count);
	}
}
