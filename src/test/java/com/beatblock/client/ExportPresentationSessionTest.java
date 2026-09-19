package com.beatblock.client;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.client.export.ExportPresentationSession;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportPresentationSessionTest {

	private TimelineEditor editor;

	@BeforeEach
	void setUp() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(90.0);
		MusicPlayer musicPlayer = new MusicPlayer();
		editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
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
	void closeRestoresTimelineSeekAfterExportFrame() {
		editor.getClock().seek(5.0);
		ExportPresentationSession session = ExportPresentationSession.begin();
		assertEquals(5.0, session.restoreTimelineTimeSeconds(), 1e-9);

		BeatBlockClientDriver.prepareExportFrame(10.0);
		assertEquals(5.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);

		session.close();
		assertEquals(5.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void closeIsIdempotent() {
		editor.getClock().seek(3.0);
		ExportPresentationSession session = ExportPresentationSession.begin();
		BeatBlockClientDriver.prepareExportFrame(8.0);

		session.close();
		session.close();

		assertEquals(3.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}
}
