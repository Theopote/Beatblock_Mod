package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.interaction.TimelineDragCommitSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Drag/resize commit must hot-reload compiled playback while driving. */
class TimelineDragCommitHotReloadTest {

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
	void commitEventDragHotReloadsCompiledPlaybackWhileDriving() {
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
	}

	@Test
	void commitClipResizeHotReloadsCompiledPlaybackWhileDriving() {
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 0.0, 4.0);
		TimelineOperations.addEvent(
			clip, 1.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 1.0, "y", 2.0, "z", 3.0, "yawDeg", 0.0, "pitchDeg", 0.0));

		ClipDragStateSnapshot before = ClipDragStateSnapshot.capture(
			timeline, Timeline.TRACK_ID_CAMERA, clip.getId(), Map.of(), Map.of());

		BeatBlockClientDriver.startDriving();
		assertEquals(4.0, firstCameraClipEnd(), 1e-9);

		clip.setEndTimeSeconds(7.0);
		TimelineDragCommitSupport.commitClipDrag(timeline, editor, before);

		assertEquals(7.0, firstCameraClipEnd(), 1e-9);
	}

	private static double firstCameraKeyframeTime() {
		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertNotNull(snapshot);
		assertEquals(1, snapshot.cameraTrack().clips().size());
		assertEquals(1, snapshot.cameraTrack().clips().getFirst().events().size());
		return snapshot.cameraTrack().clips().getFirst().events().getFirst().timeSeconds();
	}

	private static double firstCameraClipEnd() {
		var snapshot = BeatBlockClientDriver.compiledPlaybackForTests();
		assertNotNull(snapshot);
		assertEquals(1, snapshot.cameraTrack().clips().size());
		return snapshot.cameraTrack().clips().getFirst().endTimeSeconds();
	}
}
