package com.beatblock.ui.presenter;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Animation Library Apply-to-selection uses Replace Animation semantics.
 */
@WithBeatBlockContext
class AnimationLibraryPanelPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commandManager;
	private EventPropertiesPresenter eventPropertiesPresenter;
	private AnimationLibraryPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		commandManager = editor.getCommandManager();
		eventPropertiesPresenter = new EventPropertiesPresenter(
			id -> true,
			blockId -> blockId != null && blockId.startsWith("minecraft:"),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> new EventPropertiesPresenter.CameraViewSample(1, 2, 3, 90f, 0f)
		);
		presenter = new AnimationLibraryPanelPresenter(
			eventPropertiesPresenter,
			() -> timeline,
			() -> editor
		);
	}

	@Test
	void viewStateReportsSelectionReadiness() {
		assertFalse(presenter.viewState().canApplyToSelection());
		assertEquals(0, presenter.viewState().selectedAnimationEventCount());

		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 10.0);
		var event = TimelineOperations.addEvent(
			clip, 1.0, EventType.ANIMATION,
			Map.of("animationType", "Pulse", "targetObject", "stage")
		);
		editor.getSelectionState().selectEvent(event.getId());

		assertTrue(presenter.viewState().canApplyToSelection());
		assertEquals(1, presenter.viewState().selectedAnimationEventCount());
	}

	@Test
	void applyPresetReplacesAnimationAndStripsTrajectoryParams() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 20.0);
		var event = TimelineOperations.addEvent(
			clip,
			4.0,
			EventType.ANIMATION,
			Map.of(
				"animationType", "RhythmDrop",
				"targetObject", "building-a",
				"energy", 0.55f,
				"durationSeconds", 1.2,
				"eventOrigin", "MANUAL",
				"meteorHeight", 8.0,
				"impactThreshold", 0.91,
				"dispatchModel", "STEP"
			)
		);
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(event.getId());

		var outcome = presenter.applyPresetToSelection("Pulse");

		assertTrue(outcome.success());
		assertEquals("Pulse", event.getParameters().get("animationType"));
		assertEquals(
			com.beatblock.engine.influence.BlockInfluencePresets.get("Pulse").getDefaultDurationSeconds(),
			((Number) event.getParameters().get("durationSeconds")).doubleValue(),
			1e-6
		);
		assertEquals("building-a", event.getParameters().get("targetObject"));
		assertEquals(0.55f, ((Number) event.getParameters().get("energy")).floatValue(), 1e-6f);
		assertEquals("MANUAL", event.getParameters().get("eventOrigin"));
		assertEquals(4.0, event.getTimeSeconds(), 1e-9);
		assertFalse(event.getParameters().containsKey("meteorHeight"));
		assertFalse(event.getParameters().containsKey("impactThreshold"));
		assertFalse(event.getParameters().containsKey("dispatchModel"));
		assertEquals(1, commandManager.undoCount());
	}

	@Test
	void applyMissingPresetFails() {
		var outcome = presenter.applyPresetToSelection("DefinitelyNotAPreset");
		assertFalse(outcome.success());
		assertEquals(0, commandManager.undoCount());
	}

	@Test
	void applyWithoutSelectionFails() {
		var outcome = presenter.applyPresetToSelection("Pulse");
		assertFalse(outcome.success());
		assertEquals(0, commandManager.undoCount());
	}
}
