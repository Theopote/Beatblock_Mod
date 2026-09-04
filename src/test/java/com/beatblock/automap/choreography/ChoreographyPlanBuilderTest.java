package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyPlanBuilderTest {

	@Test
	void buildsMotionPhrasesFromFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.6f));
		timeline.addFeatureEvent("snare", new FeatureEvent(1.1, 0.5f));

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(timeline, AutoMapConfig.createDefault());

		assertEquals(2, plan.motionPhrases().size());
		assertEquals(0, plan.stageRoles().size());
		assertEquals("pulse", plan.motionPhrases().get(0).animationTypeId());
		assertEquals("pulse", plan.motionPhrases().get(1).animationTypeId());
		assertEquals(1.5f, plan.motionPhrases().get(0).heightMultiplier(), 1e-6f);
	}

	@Test
	void buildsDensityCurveFromStructuralSections() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("low", new FeatureEvent(0.5, 0.9f));

		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 12, SectionType.INTRO),
			new StructuralSection(12, 28, SectionType.VERSE),
			new StructuralSection(28, 36, SectionType.BUILD),
			new StructuralSection(36, 52, SectionType.DROP)
		);

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(
			timeline, AutoMapConfig.createDefault(), sections);

		assertEquals(4, plan.sections().size());
		assertTrue(plan.densityCurve().sampleAt(0) < plan.densityCurve().sampleAt(36));
	}
}
