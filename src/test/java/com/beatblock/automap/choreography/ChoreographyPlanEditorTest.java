package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.test.WithBeatBlockContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class ChoreographyPlanEditorTest {

	@Test
	void bindsSectionIndexWhenBuildingFromTimeline() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 0.6f));
		timeline.addFeatureEvent("snare", new FeatureEvent(14.0, 0.5f));

		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 12, SectionType.INTRO),
			new StructuralSection(12, 28, SectionType.DROP)
		);

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(
			timeline, AutoMapConfig.createDefault(), sections);

		assertEquals(0, plan.motionPhrases().get(0).sectionIndex());
		assertEquals(1, plan.motionPhrases().get(1).sectionIndex());
		assertEquals(1, ChoreographyPlanEditor.motionPhrasesInSection(plan, 1).size());
	}

	@Test
	void appliesSectionEditToOverrideAnimationTypeAndMuteCamera() {
		ChoreographyPlan plan = samplePlan();

		plan = ChoreographyPlanEditor.withSectionEdit(plan,
			SectionEditProfile.defaults(0)
				.withMotionAnimationType("spin")
				.withCameraEnabled(false)
		);
		plan = ChoreographyPlanEditor.bakePhraseOverrides(plan);

		assertEquals("spin", plan.motionPhrases().get(0).animationTypeId());
		assertEquals("slide", plan.motionPhrases().get(1).animationTypeId());

		Timeline timeline = Timeline.createDefault();
		int cameras = ChoreographyPlanCompiler.compileCameraEvents(timeline, plan);
		assertEquals(0, cameras);
	}

	@Test
	void shiftsPhrasesWithinSectionAndRebindsIndices() {
		ChoreographyPlan plan = samplePlan();
		plan = ChoreographyPlanEditor.shiftSection(plan, 0, 1.0);

		assertEquals(3.0, plan.motionPhrases().get(0).timeSeconds(), 1e-6);
		assertEquals(0, plan.motionPhrases().get(0).sectionIndex());
		assertEquals(14.0, plan.motionPhrases().get(1).timeSeconds(), 1e-6);
	}

	@Test
	void appliesSectionDensityThresholdDuringCompile() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(12, 28, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(2.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(14.0, "snare", "mid", 0.7f, "slide", 0.4, true, 3f, 1)
			),
			List.of(),
			List.of(),
			DensityCurve.ofPoints(List.of(
				new DensityCurve.Point(0.0, 0.2),
				new DensityCurve.Point(12.0, 0.9)
			))
		);
		plan = ChoreographyPlanEditor.withSectionEdit(plan,
			SectionEditProfile.defaults(0).withDensityThreshold(0.5)
		);

		Timeline timeline = Timeline.createDefault();
		int count = ChoreographyPlanCompiler.compileAnimationEvents(
			timeline, plan, AutoMapConfig.createDefault(), false);

		assertEquals(1, count);
		assertTrue(timeline.getAutoAnimationEvents().get(0).getTimeSeconds() >= 12.0);
	}

	@Test
	void appliesEditsToAllSectionsOfType() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro-a"),
				new ChoreographyPlan.SectionPlan(8, 16, SectionType.INTRO, "intro-b"),
				new ChoreographyPlan.SectionPlan(16, 32, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(9.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 1),
				new ChoreographyPlan.MotionPhrase(18.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 2)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);

		plan = ChoreographyPlanEditor.withSectionEditForType(
			plan,
			SectionType.INTRO,
			SectionEditProfile.defaults(0).withMotionAnimationType("fade")
		);
		plan = ChoreographyPlanEditor.bakePhraseOverrides(plan);

		assertEquals("fade", plan.motionPhrases().get(0).animationTypeId());
		assertEquals("fade", plan.motionPhrases().get(1).animationTypeId());
		assertEquals("bounce", plan.motionPhrases().get(2).animationTypeId());
	}

	@Test
	void movesSectionBoundaryAndRebindsPhraseIndices() {
		ChoreographyPlan plan = samplePlan();
		plan = ChoreographyPlanEditor.moveSectionBoundary(plan, 1, 14.0);

		assertEquals(14.0, plan.sections().get(0).endSeconds(), 1e-6);
		assertEquals(14.0, plan.sections().get(1).startSeconds(), 1e-6);
		assertEquals(0, plan.motionPhrases().get(0).sectionIndex());
		assertEquals(1, plan.motionPhrases().get(1).sectionIndex());
		assertEquals(14.0, plan.densityCurve().points().get(1).timeSeconds(), 1e-6);
	}

	@Test
	void clampsSectionBoundaryToMinimumDuration() {
		ChoreographyPlan plan = samplePlan();
		plan = ChoreographyPlanEditor.moveSectionBoundary(plan, 1, 30.0);

		double expectedMax = 28.0 - ChoreographyPlanEditor.MIN_SECTION_DURATION_SECONDS;
		assertEquals(expectedMax, plan.sections().get(0).endSeconds(), 1e-6);
		assertEquals(expectedMax, plan.sections().get(1).startSeconds(), 1e-6);

		plan = ChoreographyPlanEditor.moveSectionBoundary(plan, 1, 0.0);
		double expectedMin = ChoreographyPlanEditor.MIN_SECTION_DURATION_SECONDS;
		assertEquals(expectedMin, plan.sections().get(0).endSeconds(), 1e-6);
		assertEquals(expectedMin, plan.sections().get(1).startSeconds(), 1e-6);
	}

	private static ChoreographyPlan samplePlan() {
		return new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(12, 28, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(2.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(14.0, "snare", "mid", 0.7f, "slide", 0.4, true, 3f, 1)
			),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(ChoreographyVfxFactory.fromLegacyVfxKind(3.0, "particle_spark", 0)),
			DensityCurve.uniform(1.0)
		);
	}
}
