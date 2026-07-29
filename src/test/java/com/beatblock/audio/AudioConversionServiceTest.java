package com.beatblock.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AudioConversionServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void dispatchesProgressAndCompletionThroughConfiguredDispatcher() throws Exception {
		Path input = Files.writeString(tempDir.resolve("ready.mp3"), "audio");
		List<Runnable> queuedCallbacks = new ArrayList<>();
		List<Integer> progress = new ArrayList<>();
		AtomicReference<Path> completed = new AtomicReference<>();

		AudioConversionService service = new AudioConversionService(queuedCallbacks::add);
		try {
			service.convertToMp3Async(
				input,
				(message, percent) -> progress.add(percent),
				completed::set,
				error -> {}
			).get(5, TimeUnit.SECONDS);

			assertNull(completed.get());
			assertEquals(2, queuedCallbacks.size());
			queuedCallbacks.forEach(Runnable::run);
			assertEquals(List.of(100), progress);
			assertEquals(input, completed.get());
		} finally {
			service.shutdown();
		}
	}
}
