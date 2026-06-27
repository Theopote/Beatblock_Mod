package com.beatblock.ui.presenter;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.video.VideoExportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportPanelPresenterTest {

	@Test
	void dialogStateBlockedWithoutFfmpeg() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		MusicPlayer musicPlayer = new MusicPlayer();
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, musicPlayer))
			.build();
		VideoExportPanelPresenter presenter = new VideoExportPanelPresenter(
			() -> context,
			() -> new VideoExportService(Runnable::run)
		);

		var state = presenter.dialogState();
		assertFalse(state.canExport());
		assertTrue(state.blockedReason() != null && !state.blockedReason().isBlank());
	}
}
