package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumFrameTest {

	@Test
	void copiesMagnitudesDefensively() {
		float[] source = {0.1f, 0.9f};
		SpectrumFrame frame = new SpectrumFrame(source, 1.5);
		source[0] = 99f;

		assertEquals(2, frame.getBinCount());
		assertEquals(0.1f, frame.getMagnitudes()[0], 1e-6f);
		assertEquals(1.5, frame.getTimeSeconds(), 1e-9);
	}

	@Test
	void getMagnitudesReturnsCopy() {
		SpectrumFrame frame = new SpectrumFrame(new float[]{0.5f}, 0.0);
		float[] first = frame.getMagnitudes();
		float[] second = frame.getMagnitudes();
		assertArrayEquals(first, second);
	}
}
