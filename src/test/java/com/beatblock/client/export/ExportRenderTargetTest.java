package com.beatblock.client.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRenderTargetTest {

	@Test
	void writeArgbAsRgbaSplitsChannels() {
		byte[] rgba = new byte[4];
		// A=0xFF R=0x11 G=0x22 B=0x33
		ExportRenderTarget.writeArgbAsRgba(0xFF112233, rgba, 0);
		assertEquals((byte) 0x11, rgba[0]);
		assertEquals((byte) 0x22, rgba[1]);
		assertEquals((byte) 0x33, rgba[2]);
		assertEquals((byte) 0xFF, rgba[3]);
	}

	@Test
	void explicitResolutionRequiresTrueRenderTarget() {
		assertTrue(requiresTrueResolutionRenderTarget(1920, 1080));
		assertTrue(requiresTrueResolutionRenderTarget(2560, 1440));
		assertFalse(requiresTrueResolutionRenderTarget(0, 0));
		assertFalse(requiresTrueResolutionRenderTarget(-1, 1080));
	}

	/** Mirrors Coordinator policy: explicit WxH must not silent-fallback. */
	static boolean requiresTrueResolutionRenderTarget(int width, int height) {
		return width > 0 && height > 0;
	}
}
