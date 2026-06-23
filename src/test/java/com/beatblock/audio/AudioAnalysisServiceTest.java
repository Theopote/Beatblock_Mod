package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisServiceTest {

	@Test
	void delegatesAnalyzeToConfiguredBackend() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		AtomicReference<AnalysisOptions> capturedOptions = new AtomicReference<>();

		IAudioAnalyzer stubAnalyzer = new IAudioAnalyzer() {
			@Override
			public String backendId() {
				return "stub";
			}

			@Override
			public boolean isAvailable() {
				return true;
			}

			@Override
			public void analyze(
				Path audioPath,
				AnalysisOptions options,
				AnalysisProgressCallback onProgress,
				Consumer<Beatmap> onComplete,
				Consumer<String> onError,
				Consumer<AnalysisSummary> onSummary,
				AnalysisCancelControl control
			) {
				started.countDown();
				capturedOptions.set(options);
				onProgress.onProgress("TEST", 50);
				onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
			}
		};

		AudioAnalysisService service = new AudioAnalysisService(stubAnalyzer, new com.beatblock.audio.python.PythonEnvironmentDiagnostics());
		try {
			AtomicReference<Integer> progress = new AtomicReference<>();
			CountDownLatch done = new CountDownLatch(1);
			service.analyze(
				Path.of("test.mp3"),
				(step, pct) -> progress.set(pct),
				beatmap -> done.countDown(),
				error -> done.countDown(),
				null,
				null,
				false
			).get(5, TimeUnit.SECONDS);

			assertTrue(started.await(5, TimeUnit.SECONDS));
			assertTrue(done.await(5, TimeUnit.SECONDS));
			assertEquals(50, progress.get());
			assertEquals(false, capturedOptions.get().useDemucs());
		} finally {
			service.shutdown();
		}
	}
}
