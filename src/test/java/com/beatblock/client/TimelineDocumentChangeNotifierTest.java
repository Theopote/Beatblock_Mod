package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.editor.InteractionState;
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
		BeatBlockClientDriver.install(() -> BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build());
	}

	@AfterEach
	void tearDown() {
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
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
