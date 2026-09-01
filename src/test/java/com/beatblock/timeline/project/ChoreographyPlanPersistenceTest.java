package com.beatblock.timeline.project;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.ChoreographyVfxFactory;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyPlanPersistenceTest {

	@Test
	void roundTripsPlanConfigAndSectionEdits() {
		Timeline timeline = Timeline.createDefault();
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro", 0.72, SectionPlanSource.LOCKED),
				new ChoreographyPlan.SectionPlan(12, 28, SectionType.DROP, "drop", 0.55, SectionPlanSource.USER_EDITED)
			),
			List.of(new ChoreographyPlan.StageRoleAssignment("low", "stage-kick")),
			List.of(new ChoreographyPlan.MotionPhrase(2.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0)),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(3.0, "particle_spark", 0)),
			DensityCurve.ofPoints(List.of(
				new DensityCurve.Point(0.0, 0.2),
				new DensityCurve.Point(12.0, 0.9)
			)),
			List.of(SectionEditProfile.defaults(0).withMotionAnimationType("spin")),
			new ChoreographyPlan.MusicalStructure(
				List.of(new ChoreographyPlan.BarPlan(0, 3, 0, 0)),
				List.of(new ChoreographyPlan.MusicalPhrasePlan(0, 12, 0, 0, 0.3, -1)),
				List.of(new ChoreographyPlan.RepeatGroup(0, 0, List.of(0, 1), 0.7))
			)
		);
		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("low", "stage-kick")
			.targetForFeature("mid", "stage-snare")
			.build();
		ChoreographyPlanStore.save(timeline, plan, config);

		var json = ChoreographyPlanPersistence.toJson(timeline);
		assertNotNull(json);

		Timeline restored = Timeline.createDefault();
		ChoreographyPlanPersistence.loadInto(restored, json);

		ChoreographyPlan loadedPlan = ChoreographyPlanStore.loadPlan(restored);
		AutoMapConfig loadedConfig = ChoreographyPlanStore.loadConfig(restored);
		assertNotNull(loadedPlan);
		assertNotNull(loadedConfig);
		assertEquals(2, loadedPlan.sections().size());
		assertEquals("intro", loadedPlan.sections().get(0).label());
		assertEquals(0.72, loadedPlan.sections().get(0).confidence(), 1e-6);
		assertEquals(SectionPlanSource.LOCKED, loadedPlan.sections().get(0).source());
		assertEquals(SectionPlanSource.USER_EDITED, loadedPlan.sections().get(1).source());
		assertEquals(1, loadedPlan.motionPhrases().size());
		assertEquals("spin", loadedPlan.sectionEdits().getFirst().motionAnimationTypeOverride());
		assertEquals("stage-kick", loadedConfig.getTargetByNormalizedFeature().get("low"));
		assertEquals(1, loadedPlan.musicalStructure().bars().size());
		assertEquals(1, loadedPlan.musicalStructure().phrases().size());
		assertEquals(1, loadedPlan.musicalStructure().repeats().size());
	}
}
