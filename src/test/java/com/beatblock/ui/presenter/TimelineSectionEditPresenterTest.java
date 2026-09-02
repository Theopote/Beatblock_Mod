package com.beatblock.ui.presenter;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineSectionEditPresenterTest {

	@Test
	void unavailableWithoutStoredPlan() {
		Timeline timeline = Timeline.createDefault();
		var presenter = new TimelineSectionEditPresenter(() -> BeatBlockContext.builder().timeline(timeline).build());

		assertFalse(presenter.canEdit());
	}

	@Test
	void appliesSectionEditAndRecompilesTimeline() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 12, SectionType.INTRO, "intro")),
			List.of(),
			List.of(new ChoreographyPlan.MotionPhrase(2.0, "kick", "low", 0.8f, "bounce", 0.5, true, 4f, 0)),
			List.of(new ChoreographyPlan.CameraPhrase(4.0, "PAN", 0)),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		AutoMapConfig config = AutoMapConfig.createDefault();
		ChoreographyPlanStore.save(timeline, plan, config);

		var presenter = new TimelineSectionEditPresenter(() -> BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.build());

		assertTrue(presenter.canEdit());
		assertEquals(1, presenter.listSections().size());

		var outcome = presenter.applySectionEdit(
			0,
			SectionType.INTRO,
			false,
			SectionEditProfile.defaults(0)
				.withMotionAnimationType("spin")
				.withCameraEnabled(false)
		);

		assertTrue(outcome.result().ok());
		assertEquals(1, outcome.animationEvents());
		assertEquals(0, outcome.cameraEvents());
		assertEquals("spin", timeline.getAutoAnimationEvents().getFirst().getAnimationTypeId());
	}

	@Test
	void sectionEditRecompilesOnlyEditedSection() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, SectionType.INTRO, "intro"),
				new ChoreographyPlan.SectionPlan(8, 16, SectionType.DROP, "drop")
			),
			List.of(),
			List.of(
				new ChoreographyPlan.MotionPhrase(1.0, "intro", "low", 0.6f, "bounce", 0.5, true, 4f, 0),
				new ChoreographyPlan.MotionPhrase(9.0, "drop", "low", 0.8f, "pulse", 0.5, true, 4f, 1)
			),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
		AutoMapConfig config = AutoMapConfig.createDefault();
		ChoreographyPlanStore.save(timeline, plan, config);
		com.beatblock.automap.choreography.ChoreographyPlanCompiler.compileAll(
			timeline, plan, com.beatblock.automap.choreography.ChoreographyCompileOptions.smartAutoMap());

		var presenter = new TimelineSectionEditPresenter(() -> BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.build());

		var outcome = presenter.applySectionEdit(
			0,
			SectionType.INTRO,
			false,
			SectionEditProfile.defaults(0).withMotionAnimationType("spin")
		);

		assertTrue(outcome.result().ok());
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		assertEquals("spin", timeline.getAutoAnimationEvents().get(0).getAnimationTypeId());
		assertEquals("pulse", timeline.getAutoAnimationEvents().get(1).getAnimationTypeId());
	}

	@Test
	void appliesSpatialMotifOverrideToSection() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 12, SectionType.BUILD, "build")),
			List.of(
				new ChoreographyPlan.StageRoleAssignment("low", "tower-a"),
				new ChoreographyPlan.StageRoleAssignment("mid", "tower-b")
			),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(new SpatialMotifPhrase(
				0.05,
				SpatialMotifId.CASCADE,
				List.of("tower-a", "tower-b"),
				com.beatblock.automap.choreography.MotifAxis.X,
				0.06,
				"pulse",
				0.75f,
				0.5,
				0
			))
		);
		ChoreographyPlanStore.save(timeline, plan, AutoMapConfig.createDefault());

		var presenter = new TimelineSectionEditPresenter(() -> BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.build());

		SectionEditProfile edit = presenter.applySpatialMotifDropdownIndex(
			SectionEditProfile.defaults(0).withMotionAnimationType("pulse"),
			TimelineSectionEditPresenter.indexOfSpatialMotif(SpatialMotifId.WAVE)
		);

		var outcome = presenter.applySectionEdit(0, SectionType.BUILD, false, edit);
		assertTrue(outcome.result().ok());

		ChoreographyPlan updated = ChoreographyPlanStore.loadPlan(timeline);
		assertEquals(SpatialMotifId.WAVE, updated.spatialMotifPhrases().getFirst().motifId());
		assertEquals(2, outcome.animationEvents());
	}
}
