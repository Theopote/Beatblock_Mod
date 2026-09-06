package com.beatblock.ui.presenter;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.video.VideoExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportPanelPresenterTest {

	@TempDir
	Path tempDir;

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
			() -> VideoExportService.createForTesting(),
			() -> new VideoExportPanelPresenter.FfmpegStatus(false, null, "not found")
		);

		var state = presenter.dialogState();
		assertFalse(state.canExport());
		assertTrue(state.blockedReason() != null && !state.blockedReason().isBlank());
	}

	@Test
	void startExportWithIncludeAudioFailsWhenSourceMissing() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		timeline.setMetadata("audioPath", tempDir.resolve("missing.wav").toString());
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.build();
		VideoExportPanelPresenter presenter = new VideoExportPanelPresenter(
			() -> context,
			() -> VideoExportService.createForTesting(),
			() -> new VideoExportPanelPresenter.FfmpegStatus(true, "ffmpeg", "ok")
		);

		var result = presenter.startExport(
			tempDir.resolve("out.mp4").toString(),
			0,
			0,
			0,
			0.0,
			10.0,
			true
		);
		assertFalse(result.ok());
		assertTrue(result.messageOrEmpty().toLowerCase().contains("audio")
			|| result.messageOrEmpty().contains("音频"));
	}

	@Test
	void dialogStateReportsAudioAvailability() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10.0);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.build();
		VideoExportPanelPresenter presenter = new VideoExportPanelPresenter(
			() -> context,
			() -> VideoExportService.createForTesting(),
			() -> new VideoExportPanelPresenter.FfmpegStatus(true, "ffmpeg", "ok")
		);

		var state = presenter.dialogState();
		assertFalse(state.audioSourceAvailable());
	}

	@Test
	void startExportRequiresReplaceConfirmWhenOutputExists() throws Exception {
		Path existing = tempDir.resolve("exists.mp4");
		Files.writeString(existing, "old", StandardCharsets.UTF_8);
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.build();
		VideoExportPanelPresenter presenter = new VideoExportPanelPresenter(
			() -> context,
			() -> VideoExportService.createForTesting(),
			() -> new VideoExportPanelPresenter.FfmpegStatus(true, "ffmpeg", "ok")
		);

		assertTrue(presenter.requiresReplaceConfirm(existing.toString()));
		var blocked = presenter.startExport(
			existing.toString(), 0, 0, 0, 0.0, 10.0, false, false);
		assertFalse(blocked.ok());
		assertTrue(blocked.messageOrEmpty().toLowerCase().contains("replace")
			|| blocked.messageOrEmpty().contains("替换")
			|| blocked.messageOrEmpty().contains("存在"));
	}

	@Test
	void dialogStateBlocksWhenPreflightHasErrors() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"x", 1.0, 1.0, "NoSuchPreset", "t", 1f,
			Map.of("animationType", "NoSuchPreset", "targetObject", "t", "durationSeconds", 1.0)));
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.blockAnimationEngine(engine)
			.build();
		VideoExportPanelPresenter presenter = new VideoExportPanelPresenter(
			() -> context,
			() -> VideoExportService.createForTesting(),
			() -> new VideoExportPanelPresenter.FfmpegStatus(true, "ffmpeg", "ok")
		);

		presenter.refreshPreflight();
		var state = presenter.dialogState();
		assertFalse(state.canExport());
		assertFalse(state.preflight().canExport());
		assertTrue(state.preflight().errorCount() >= 1 || !state.preflight().blockers().isEmpty());
	}
}
