package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaveformPreviewTest {

	@Test
	void storesSamplesPerSecondAndData() {
		float[] data = {0.2f, 0.8f, 0.1f};
		WaveformPreview preview = new WaveformPreview(10, data);
		assertEquals(10, preview.samplesPerSecond());
		assertArrayEquals(data, preview.data(), 1e-6f);
	}
}
