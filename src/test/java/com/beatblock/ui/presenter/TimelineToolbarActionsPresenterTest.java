package com.beatblock.ui.presenter;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineToolbarActionsPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private TimelineToolbarActionsPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		presenter = new TimelineToolbarActionsPresenter(
			() -> timeline,
			() -> editor,
			() -> Vec3d.ZERO
		);
	}

	@Test
	void runBindingMapReturnsOutcomeOnEmptyTimeline() {
		var outcome = presenter.runBindingMap();
		assertFalse(outcome.success());
		assertEquals(0, outcome.count());
		assertTrue(outcome.message().contains("Binding Map"));
	}

	@Test
	void runBindingMapGeneratesEventsWhenRulesMatchFeatures() {
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 0.8f));
		AnimationBindingEngine.saveRules(timeline, List.of(
			AnimationBindingRule.builder()
				.sourceFeatureKey("kick")
				.animationTypeId("Pulse")
				.targetObjectId("stage-a")
				.energyThreshold(0.2f)
				.probability(1.0f)
				.build()
		));

		var outcome = presenter.runBindingMap();

		assertTrue(outcome.success());
		assertEquals(1, outcome.count());
		assertEquals(1, timeline.getAnimationEvents(Timeline.blockAnimationFeatureTrackId("kick")).size());
	}

	@Test
	void runAutoMapReturnsOutcomeOnEmptyTimeline() {
		var outcome = presenter.runAutoMap();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("Auto Map"));
	}

	@Test
	void runAutoMapSkippedWhenTimelineMissing() {
		var missing = new TimelineToolbarActionsPresenter(() -> null, () -> editor, () -> Vec3d.ZERO);
		var outcome = missing.runAutoMap();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("skipped"));
	}

	@Test
	void runBakeStepSkippedWhenTimelineMissing() {
		var missing = new TimelineToolbarActionsPresenter(() -> null, () -> editor, () -> Vec3d.ZERO);
		var outcome = missing.runBakeStepSequences();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("skipped"));
	}

	@Test
	void runBakeStepReportsNothingToBake() {
		var outcome = presenter.runBakeStepSequences();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("Bake STEP"));
	}

	@Test
	void runGenerateRhythmDropsFailsWithoutSelection() {
		var outcome = presenter.runGenerateRhythmDrops();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("落点"));
	}

	@Test
	void runBindingMapFailsWhenTimelineMissing() {
		var missing = new TimelineToolbarActionsPresenter(() -> null, () -> editor, () -> Vec3d.ZERO);
		var outcome = missing.runBindingMap();
		assertFalse(outcome.success());
	}
}
