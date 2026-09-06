package com.beatblock.client.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
