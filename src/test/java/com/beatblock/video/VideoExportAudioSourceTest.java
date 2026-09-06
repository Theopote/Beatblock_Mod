package com.beatblock.video;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportAudioSourceTest {

	@TempDir
	Path tempDir;

	@Test
	void resolvesExistingTimelineAudioPath() throws Exception {
		Path audio = tempDir.resolve("song.mp3");
		Files.writeString(audio, "fake");
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("audioPath", audio.toString());
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.build();

		assertTrue(VideoExportAudioSource.isAvailable(context));
		assertNotNull(VideoExportAudioSource.resolve(context));
	}

	@Test
	void missingAudioPathIsUnavailable() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("audioPath", tempDir.resolve("missing.mp3").toString());
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(new TimelineEditor(timeline, new MusicPlayer()))
			.build();

		assertFalse(VideoExportAudioSource.isAvailable(context));
		assertNull(VideoExportAudioSource.resolve(context));
	}
}
