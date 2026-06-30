package com.beatblock.audio.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioDecoderTest {

	@Test
	void pcmS16LeToMonoFloat_averagesStereoChannels() {
		byte[] pcm = {
			(byte) 0x00, (byte) 0x40, // +16384
			(byte) 0x00, (byte) 0xC0, // -16384
		};
		float[] mono = AudioDecoder.pcmS16LeToMonoFloat(pcm, 2);
		assertEquals(1, mono.length);
		assertEquals(0f, mono[0], 1e-6f);
	}

	@Test
	void pcmS16LeToMonoFloat_scalesMonoSample() {
		byte[] pcm = {(byte) 0x00, (byte) 0x40}; // +16384
		float[] mono = AudioDecoder.pcmS16LeToMonoFloat(pcm, 1);
		assertEquals(1, mono.length);
		assertEquals(0.5f, mono[0], 1e-6f);
	}
}
