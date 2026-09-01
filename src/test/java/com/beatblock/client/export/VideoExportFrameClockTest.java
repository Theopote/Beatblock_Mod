package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.video.VideoExportSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VideoExportFrameClockTest {

	@Test
	void frame600At60FpsMapsToTenSeconds() {
		VideoExportSettings settings = settings(0.0, 20.0, 60);
		assertEquals(10.0, VideoExportFrameClock.timelineTimeSeconds(settings, 600), 1e-9);
	}

	@Test
	void audioSamplePositionMatchesTimelineAtTenSeconds() {
		VideoExportSettings settings = settings(0.0, 20.0, 60);
		long sampleIndex = VideoExportFrameClock.audioSampleIndex(settings, 600, 44_100);
		assertEquals(441_000L, sampleIndex);
		assertEquals(10.0, VideoExportFrameClock.audioTimeFromSampleIndex(sampleIndex, 44_100), 1e-9);
	}

	@Test
	void exportOffsetPreservesAudioAlignmentAtLaterFrames() {
		VideoExportSettings settings = settings(5.0, 25.0, 60);
		assertEquals(10.0, VideoExportFrameClock.timelineTimeSeconds(settings, 300), 1e-9);
		assertEquals(10.0, VideoExportFrameClock.audioSourceTimeSeconds(settings, 300), 1e-9);
		assertEquals(5.0, VideoExportFrameClock.audioSourceTimeSeconds(settings, 0), 1e-9);
	}

	private static VideoExportSettings settings(double start, double end, int fps) {
		return new VideoExportSettings(Path.of("out/export.mp4"), 1920, 1080, fps, start, end, true);
	}
}
