package com.beatblock.client.export;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportChunkPreloaderTest {

	@Test
	void noWorldOrEmptyPositionsIsNoOp() {
		assertEquals(0, ExportChunkPreloader.ensureChunksLoadedAndAwait(
			null, List.of(new BlockPos(0, 64, 0)), Duration.ofSeconds(1)));
		assertEquals(0, ExportChunkPreloader.ensureChunksLoadedAndAwait(
			null, List.of(), Duration.ofSeconds(1)));
	}
}
