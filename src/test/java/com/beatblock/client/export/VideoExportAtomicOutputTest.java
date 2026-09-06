package com.beatblock.client.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportAtomicOutputTest {

	@TempDir
	Path tempDir;

	@Test
	void createTempOutputUsesSiblingUniqueBeatblockSuffix() throws Exception {
		Path finalOut = tempDir.resolve("my_performance.mp4");
		Path temp = VideoExportAtomicOutput.createTempOutput(finalOut);
		assertTrue(Files.exists(temp));
		assertEquals(tempDir.toAbsolutePath().normalize(), temp.getParent());
		assertTrue(temp.getFileName().toString().startsWith("my_performance.mp4."));
		assertTrue(temp.getFileName().toString().endsWith(VideoExportAtomicOutput.TEMP_SUFFIX));
		assertFalse(Files.exists(finalOut));
	}

	@Test
	void promoteReplacesFinalAndRemovesTemp() throws Exception {
		Path finalOut = tempDir.resolve("out.mp4");
		Path temp = VideoExportAtomicOutput.createTempOutput(finalOut);
		Files.writeString(temp, "encoded", StandardCharsets.UTF_8);
		Files.writeString(finalOut, "old", StandardCharsets.UTF_8);

		VideoExportAtomicOutput.promote(temp, finalOut);

		assertFalse(Files.exists(temp));
		assertEquals("encoded", Files.readString(finalOut));
	}

	@Test
	void deleteQuietlyRemovesPartialTemp() throws Exception {
		Path finalOut = tempDir.resolve("clip.mp4");
		Path temp = VideoExportAtomicOutput.createTempOutput(finalOut);
		Files.writeString(temp, "partial", StandardCharsets.UTF_8);
		VideoExportAtomicOutput.deleteQuietly(temp);
		assertFalse(Files.exists(temp));
		assertFalse(Files.exists(finalOut));
	}
}
