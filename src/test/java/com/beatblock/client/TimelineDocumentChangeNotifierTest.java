package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.generation.AnimationPresetEventWriter;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.interaction.TimelineDragCommitSupport;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.TimelineEditorPresenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Properties {@code afterDocumentEdit} and Timeline drag/undo share
 * {@link TimelineDocumentChangeNotifier}.
 */
class TimelineDocumentChangeNotifierTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private MusicPlayer musicPlayer;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(60.0);
		musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		BeatBlock.installContext(context);
		BeatBlockClientDriver.install(() -> context);
	}

	@AfterEach
	void tearDown() {
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
		BeatBlock.resetContext();
	}

	@Test
	void dragCommitAndUndoBothRefreshCompiledPlaybackWhileDriving() {
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 10.0);
		TimelineEvent keyframe = TimelineOperations.addEvent(
			clip, 1.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 1.0, "y", 2.0, "z", 3.0, "yawDeg", 0.0, "pitchDeg", 0.0));

		BeatBlockClientDriver.startDriving();
		assertEquals(1.0, firstCameraKeyframeTime(), 1e-9);

		keyframe.setTimeSeconds(4.0);
		InteractionState interaction = new InteractionState();
		interaction.setActiveTrackId(Timeline.TRACK_ID_CAMERA);
		interaction.setActiveClipId(clip.getId());
		interaction.setActiveEventId(keyframe.getId());
		TimelineDragCommitSupport.commitEventDrag(timeline, editor, interaction, 1.0);
		assertEquals(4.0, firstCameraKeyframeTime(), 1e-9);

		TimelineEditorPresenter presenter = new TimelineEditorPresenter(() -> editor, t -> {});
		assertTrue(presenter.undo());
		assertEquals(1.0, firstCameraKeyframeTime(), 1e-9);
	}

	@Test
	void animationLibraryDropRefreshesCompiledPlaybackWhileDriving() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());

		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			"BlockTap",
			20.0,
			List.of("stage")
		);

		assertEquals(1, result.written());
		assertEquals(1, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());
		assertEquals(20.0,
			BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().getFirst().event().getTimeSeconds(),
			1e-9);
	}

	@Test
	void insertManualEventsRefreshesCompiledPlaybackWhileDriving() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());

		int written = TimelineDraftWriter.insertManualEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			List.of(new TimelineAnimationEvent(
				"ev1", 20.0, 0.35, "BlockTap", "stage", 1f, Map.of()))
		).written();

		assertEquals(1, written);
		assertEquals(1, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());
	}

	@Test
	void insertManualCameraShotRefreshesCompiledPlaybackWhileDriving() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().cameraTrack().clips().size());

		var draft = com.beatblock.automap.camera.CameraShotDraft.fromLivePose(
			2.0,
			com.beatblock.automap.camera.CameraShotInsertionService.DEFAULT_PATH_DURATION_SECONDS,
			com.beatblock.automap.camera.CameraShotMovement.HOLD,
			new com.beatblock.automap.camera.CapturedCameraPose(0, 64, 0, 0, 0)
		);
		var result = com.beatblock.automap.camera.CameraShotInsertionService.insertManualDraft(
			timeline, editor, draft);

		assertTrue(result.written());
		assertEquals(1, BeatBlockClientDriver.compiledPlaybackForTests().cameraTrack().clips().size());
	}

	@Test
	void insertManualGlobalEffectRefreshesCompiledPlaybackWhileDriving() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().globalEvents().size());

		var result = com.beatblock.automap.vfx.GlobalEventInsertionService.insertManual(
			timeline,
			editor,
			new com.beatblock.automap.vfx.GlobalEventCreationRequest(
				8.0,
				new com.beatblock.timeline.playback.GlobalEventPayload.ScreenTint(
					"Warm", 0.4, 1f, 0.8f, 0.5f, 5.0)
			)
		);

		assertTrue(result.written());
		assertEquals(1, BeatBlockClientDriver.compiledPlaybackForTests().globalEvents().size());
		assertEquals(8.0,
			BeatBlockClientDriver.compiledPlaybackForTests().globalEvents().getFirst().timeSeconds(),
			1e-9);
	}

	@Test
	void applyEnvironmentPresetRefreshesCompiledPlaybackWhileDriving() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().globalEvents().size());

		var result = com.beatblock.automap.vfx.GlobalEventInsertionService.applyPreset(
			timeline, editor, com.beatblock.automap.vfx.EnvironmentPreset.storm(), 10.0);

		assertTrue(result.written());
		assertEquals(3, BeatBlockClientDriver.compiledPlaybackForTests().globalEvents().size());
	}

	@Test
	void insertGeneratedEventsDoesNotRefreshCompiledPlaybackByItself() {
		BeatBlockClientDriver.startDriving();
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());

		int written = TimelineDraftWriter.insertGeneratedEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_AUTO,
			List.of(new TimelineAnimationEvent(
				"gen1", 5.0, 1.0, "build", "stage", 1f, Map.of()))
		);

		assertEquals(1, written);
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals(0, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());
	}

	@Test
	void propertiesPresenterWiresSharedNotifier() {
		EventPropertiesPresenter presenter = new EventPropertiesPresenter(
			id -> true,
			id -> true,
			List::of,
			List::of,
			() -> null,
			TimelineDocumentChangeNotifier::notifyDocumentEdited
		);
		assertNotNull(presenter);
	}

	private static double firstCameraKeyframeTime() {
		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertNotNull(snapshot);
		assertEquals(1, snapshot.cameraTrack().clips().size());
		assertEquals(1, snapshot.cameraTrack().clips().getFirst().events().size());
		return snapshot.cameraTrack().clips().getFirst().events().getFirst().timeSeconds();
	}
}
