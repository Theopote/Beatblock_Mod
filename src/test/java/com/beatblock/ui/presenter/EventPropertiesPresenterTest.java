package com.beatblock.ui.presenter;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPropertiesPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commandManager;
	private EventPropertiesPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		commandManager = editor.getCommandManager();
		presenter = new EventPropertiesPresenter(
			id -> "target-1".equals(id),
			blockId -> blockId != null && blockId.startsWith("minecraft:"),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> new EventPropertiesPresenter.CameraViewSample(1, 2, 3, 90f, 0f)
		);
	}

	@Test
	void resolvePropertiesRefFromSelectedAudioClip() {
		Track audio = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		var clip = TimelineOperations.addClip(audio, 0.0, 6.0);
		timeline.setMetadata("clipLabel_" + clip.getId(), "Intro");
		timeline.setMetadata("clipAudioPath_" + clip.getId(), "C:/music/intro.wav");

		SelectionState selection = editor.getSelectionState();
		selection.selectClip(clip.getId());

		EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, selection);
		assertNotNull(ref);
		assertEquals(clip.getId(), ref.clip().getId());
		assertNull(ref.event());
		assertEquals(Timeline.TRACK_ID_AUDIO, ref.track().getId());
	}

	@Test
	void resolvePropertiesRefPrefersSelectedEvent() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(
			clip,
			1.0,
			com.beatblock.timeline.EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "target-1")
		);
		String eventId = event.getId();

		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(eventId);

		EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, selection);
		assertNotNull(ref);
		assertEquals(eventId, ref.event().getId());
		assertEquals(track.getId(), ref.track().getId());
	}

	@Test
	void resolvePropertiesRefFromSelectedCameraClipWithoutEvent() {
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertNotNull(cam);
		var clip = TimelineOperations.addClip(cam, 0.0, 4.0);

		SelectionState selection = editor.getSelectionState();
		selection.selectClip(clip.getId());

		EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, selection);
		assertNotNull(ref);
		assertEquals(clip.getId(), ref.clip().getId());
		assertNull(ref.event());
	}

	@Test
	void isTrackLockedReflectsTrackListState() {
		TimelineTrackListState trackListState = editor.getTrackListState();
		trackListState.setLocked(TimelineTrackMeta.ROW_CAMERA, true);

		assertTrue(presenter.isTrackLocked(timeline, editor, Timeline.TRACK_ID_CAMERA));
		assertFalse(presenter.isTrackLocked(timeline, editor, Timeline.TRACK_ID_AUDIO));
	}

	@Test
	void applyAnimationEventUpdatesEventParameters() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(
			clip,
			0.0,
			com.beatblock.timeline.EventType.ANIMATION,
			Map.of("animationType", "old", "targetObject", "target-1")
		);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		AnimationEventFormInput input = new AnimationEventFormInput(
			1.5,
			0.5,
			0.8f,
			0.2f,
			"ANIMATE",
			"pulse",
			"target-1",
			true,
			"ALL",
			0.0,
			false,
			"NEXT_BEAT",
			"KEEP",
			"BEAT_GRID",
			1,
			0.25,
			0.05,
			false,
			false,
			0.0,
			false,
			20.0,
			60.0,
			20.0,
			8.0,
			48.0,
			0.6,
			1.5,
			"minecraft:diamond_block",
			"minecraft:gold_block",
			true
		);

		var result = presenter.applyAnimationEvent(ref, timeline, commandManager, input);
		assertTrue(result instanceof EventPropertiesPresenter.ApplyResult.Ok);
		assertEquals(1.5, event.getTimeSeconds(), 1e-9);
		assertEquals("pulse", event.getParameters().get("animationType"));
		assertEquals("target-1", event.getParameters().get("targetObject"));
	}

	@Test
	void captureSegmentViewParamsReturnsDollyDefaultsFromCameraProvider() {
		Optional<Map<String, String>> captured = presenter.captureSegmentViewParams(
			com.beatblock.timeline.camera.CameraSegmentKind.DOLLY
		);
		assertTrue(captured.isPresent());
		assertEquals("1.00", captured.get().get("startX"));
		assertEquals("90.00", captured.get().get("baseYawDeg"));
	}

	@Test
	void actionOptionsIncludeAllModes() {
		List<EventPropertiesOption> options = presenter.actionOptions();
		assertFalse(options.isEmpty());
		assertTrue(options.stream().anyMatch(option -> "ANIMATE".equals(option.id())));
		assertTrue(options.stream().anyMatch(option -> "BUILD".equals(option.id())));
	}

	@Test
	void buildFormSnapshotFormatsAnimationEventFields() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(
			clip,
			2.5,
			EventType.ANIMATION,
			Map.of(
				"animationType", "pulse",
				"targetObject", "target-1",
				"durationSeconds", 0.75,
				"energy", 0.5
			)
		);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		EventPropertiesFormSnapshot snapshot = presenter.buildFormSnapshot(ref, timeline);
		assertEquals("2.50", snapshot.time());
		assertEquals("0.75", snapshot.duration());
		assertEquals("0.50", snapshot.energy());
		assertEquals("pulse", presenter.readAnimationEditorState(event.getParameters()).animationId());
	}

	@Test
	void buildFormSnapshotIncludesRhythmDropTrajectoryFields() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(
			clip,
			1.0,
			EventType.ANIMATION,
			Map.of(
				"animationType", "RhythmDrop",
				"targetObject", "target-1",
				"singleBlockX", 5,
				"singleBlockY", 64,
				"singleBlockZ", -2,
				"meteorHeight", 8.0,
				"impactThreshold", 0.93
			)
		);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		EventPropertiesFormSnapshot snapshot = presenter.buildFormSnapshot(ref, timeline);
		assertEquals("5", snapshot.singleBlockX());
		assertEquals("64", snapshot.singleBlockY());
		assertEquals("-2", snapshot.singleBlockZ());
		assertEquals("8.00", snapshot.meteorHeight());
		assertEquals("0.93", snapshot.impactThreshold());
	}

	@Test
	void buildFormSnapshotFormatsCameraClipOnlySelection() {
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var clip = TimelineOperations.addClip(cam, 1.0, 5.0);
		EventPropertiesRef ref = new EventPropertiesRef(cam, clip, null);

		EventPropertiesFormSnapshot snapshot = presenter.buildFormSnapshot(ref, timeline);
		assertEquals("1.00", snapshot.camClipStart());
		assertEquals("5.00", snapshot.camClipEnd());
	}

	@Test
	void applyBatchAnimationEditUpdatesEnergyAndTimeOffset() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 10.0);
		var first = TimelineOperations.addEvent(
			clip,
			1.0,
			EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "target-1", "energy", 0.3)
		);
		var second = TimelineOperations.addEvent(
			clip,
			3.0,
			EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "target-1", "energy", 0.3)
		);

		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(first.getId());
		selection.selectEvent(second.getId());

		var outcome = presenter.applyBatchAnimationEdit(
			timeline,
			selection,
			commandManager,
			new EventPropertiesPresenter.BatchAnimationEditRequest(
				0.9f, null, 0.5, null, null, null, null)
		);
		assertTrue(outcome.success());
		assertEquals(2, outcome.updatedCount());
		assertEquals(0.9f, ((Number) first.getParameters().get("energy")).floatValue(), 1e-6f);
		assertEquals(0.9f, ((Number) second.getParameters().get("energy")).floatValue(), 1e-6f);
		assertEquals(1.5, first.getTimeSeconds(), 1e-9);
		assertEquals(3.5, second.getTimeSeconds(), 1e-9);
	}

	@Test
	void countSelectedAnimationEventsIgnoresNonAnimationSelection() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var animation = TimelineOperations.addEvent(
			clip,
			1.0,
			EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "target-1")
		);
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var camClip = TimelineOperations.addClip(cam, 0.0, 2.0);
		var marker = TimelineOperations.addEvent(
			camClip,
			0.5,
			EventType.CAMERA_KEYFRAME,
			Map.of("x", 0.0, "y", 64.0, "z", 0.0)
		);

		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(animation.getId());
		selection.selectEvent(marker.getId());

		assertEquals(1, presenter.countSelectedAnimationEvents(timeline, selection));
	}

	@Test
	void applyBatchAnimationEditScalesDurationPreservingRelativeDifferences() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 20.0);
		var shortEvent = TimelineOperations.addEvent(
			clip, 1.0, EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "t", "durationSeconds", 1.0)
		);
		var longEvent = TimelineOperations.addEvent(
			clip, 3.0, EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "t", "durationSeconds", 2.0)
		);
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(shortEvent.getId());
		selection.selectEvent(longEvent.getId());

		var outcome = presenter.applyBatchAnimationEdit(
			timeline, selection, commandManager,
			new EventPropertiesPresenter.BatchAnimationEditRequest(
				null, null, null, null, 2.0, null, null)
		);
		assertTrue(outcome.success());
		assertEquals(2.0, ((Number) shortEvent.getParameters().get("durationSeconds")).doubleValue(), 1e-6);
		assertEquals(4.0, ((Number) longEvent.getParameters().get("durationSeconds")).doubleValue(), 1e-6);
	}

	@Test
	void applyBatchAnimationEditSetsActionModeAndPlaceBlock() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 10.0);
		var event = TimelineOperations.addEvent(
			clip, 1.0, EventType.ANIMATION,
			Map.of(
				"animationType", "build",
				"targetObject", "t",
				"actionMode", "BUILD",
				"mode", "BUILD",
				"placeBlock", "minecraft:diamond_block",
				"buildMode", "wall"
			)
		);
		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(event.getId());

		var outcome = presenter.applyBatchAnimationEdit(
			timeline, selection, commandManager,
			new EventPropertiesPresenter.BatchAnimationEditRequest(
				null, null, null,
				TimelineAnimationActionMode.PLACE,
				null, null,
				Map.of("placeBlock", "minecraft:gold_block"))
		);
		assertTrue(outcome.success());
		assertEquals("PLACE", event.getParameters().get("actionMode"));
		assertEquals("PLACE", event.getParameters().get("mode"));
		assertEquals("minecraft:gold_block", event.getParameters().get("placeBlock"));
		assertEquals("wall", event.getParameters().get("buildMode"));
	}
}
