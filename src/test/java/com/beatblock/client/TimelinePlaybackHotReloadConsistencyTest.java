package com.beatblock.client;

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
import com.beatblock.timeline.playback.PlaybackSemantics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formal playback hot-reload must reconstruct at the playhead after {@code load()},
 * otherwise the next advance would re-dispatch already-passed events from t=0.
 */
class TimelinePlaybackHotReloadConsistencyTest {

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
	void hotReloadReconstructsPastStatefulEventsAtPlayhead() {
		timeline.addAutoAnimationEvent(stageEvent("early", 1.0));
		timeline.addAutoAnimationEvent(stageEvent("later", 5.0));

		BeatBlockClientDriver.startDriving();
		assertNotNull(BeatBlockClientDriver.compiledPlaybackForTests());
		assertEquals(2, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());

		editor.getPlaybackSession().seek(3.0);
		BeatBlockClientDriver.advanceFormalPlaybackForTests(3.0);
		int scheduledBefore = BeatBlockClientDriver.scheduledStageCountForTests();
		assertTrue(scheduledBefore >= 1, "expected past stage event to be scheduled before reload");

		// Document mutation while driving → hot-reload compiled program.
		timeline.addAutoAnimationEvent(stageEvent("extra", 8.0));
		TimelineDocumentChangeNotifier.notifyDocumentEdited();

		assertEquals(3, BeatBlockClientDriver.compiledPlaybackForTests().compiledStageEvents().size());
		// load() alone would clear the schedule; reconstruct must restore past STATEFUL events.
		assertTrue(
			BeatBlockClientDriver.scheduledStageCountForTests() >= 1,
			"hot-reload must reconstruct at playhead so past events stay scheduled");

		int scheduledAfterReload = BeatBlockClientDriver.scheduledStageCountForTests();
		BeatBlockClientDriver.advanceFormalPlaybackForTests(3.01);
		assertEquals(
			scheduledAfterReload,
			BeatBlockClientDriver.scheduledStageCountForTests(),
			"tiny advance after reconstruct must not re-dispatch the past event set");
	}

	@Test
	void cutWhileDrivingHotReloadsCompiledCameraTrack() {
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 10.0);
		TimelineEvent keyframe = TimelineOperations.addEvent(
			clip, 2.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 1.0, "y", 2.0, "z", 3.0, "yawDeg", 0.0, "pitchDeg", 0.0));

		BeatBlockClientDriver.startDriving();
		assertEquals(2.0, firstCameraKeyframeTime(), 1e-9);

		editor.getSelectionState().selectEvent(keyframe.getId());
		editor.getEditSession().deleteSelection();

		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertNotNull(snapshot);
		assertTrue(snapshot.cameraTrack().clips().isEmpty()
			|| snapshot.cameraTrack().clips().getFirst().events().isEmpty());
	}

	private static TimelineAnimationEvent stageEvent(String id, double timeSeconds) {
		return new TimelineAnimationEvent(
			id,
			timeSeconds,
			1.0,
			id,
			"stage",
			1f,
			Map.of("playbackSemantics", PlaybackSemantics.STATEFUL.name()));
	}

	private static double firstCameraKeyframeTime() {
		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertNotNull(snapshot);
		assertEquals(1, snapshot.cameraTrack().clips().size());
		assertEquals(1, snapshot.cameraTrack().clips().getFirst().events().size());
		return snapshot.cameraTrack().clips().getFirst().events().getFirst().timeSeconds();
	}
}
