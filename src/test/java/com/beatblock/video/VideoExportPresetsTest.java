package com.beatblock.video;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoExportPresetsTest {

	@Test
	void fromPresetUsesEndTimeSecondsNotFrameCount() {
		VideoExportSettings settings = VideoExportPresets.fromPreset(
			VideoExportPresets.PresetType.YOUTUBE_1080P,
			Path.of("out/export.mp4"),
			10.0,
			15.0,
			true
		);

		assertEquals(10.0, settings.startTimeSeconds(), 1e-6);
		assertEquals(15.0, settings.endTimeSeconds(), 1e-6);
		assertEquals(5.0, settings.durationSeconds(), 1e-6);
		assertEquals(300, settings.totalFrames());
	}

	@Test
	void fromPresetComputesFramesFromDuration() {
		VideoExportSettings settings = VideoExportPresets.fromPreset(
			VideoExportPresets.PresetType.TIKTOK_VERTICAL,
			Path.of("out/vertical.mp4"),
			0.0,
			5.0,
			false
		);

		assertEquals(5.0, settings.durationSeconds(), 1e-6);
		assertEquals(150, settings.totalFrames());
	}
}
