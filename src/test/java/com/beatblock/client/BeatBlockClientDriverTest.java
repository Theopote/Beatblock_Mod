package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatBlockClientDriverTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private MusicPlayer musicPlayer;
	private BeatBlockContext context;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(90.0);
		musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		BeatBlockClientDriver.install(() -> context);
	}

	@AfterEach
	void tearDown() {
		BeatBlockClientDriver.stopDriving();
		BeatBlockClientDriver.resetForTests();
	}

	@Test
	void previewTimelineTimeSecondsUsesInjectedEditorClock() {
		editor.getClock().seek(12.5);
		assertEquals(12.5, BeatBlockClientDriver.previewTimelineTimeSeconds(), 1e-9);
	}

	@Test
	void drivingLifecycleUsesInjectedMusicPlayer() {
		assertFalse(BeatBlockClientDriver.isDriving());
		BeatBlockClientDriver.startDriving();
		assertTrue(BeatBlockClientDriver.isDriving());
		BeatBlockClientDriver.stopDriving();
		assertFalse(BeatBlockClientDriver.isDriving());
	}

	@Test
	void stopPlaybackPausesMusicAndClearsDriving() {
		musicPlayer.setDurationSeconds(30.0);
		musicPlayer.play();
		BeatBlockClientDriver.startDriving();
		BeatBlockClientDriver.stopPlayback();
		assertFalse(BeatBlockClientDriver.isDriving());
		assertFalse(musicPlayer.isPlaying());
	}
}
