package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisOrchestratorTest {

	@Test
	void runsTasksSequentiallyOnSingleWorker() throws Exception {
		AtomicInteger concurrent = new AtomicInteger();
		AtomicInteger maxConcurrent = new AtomicInteger();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondDone = new CountDownLatch(1);

		IAudioAnalyzer blockingAnalyzer = new IAudioAnalyzer() {
			@Override
			public String backendId() {
				return "blocking";
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
				int active = concurrent.incrementAndGet();
				maxConcurrent.updateAndGet(current -> Math.max(current, active));
				try {
					if ("first.mp3".equals(String.valueOf(audioPath.getFileName()))) {
						firstStarted.countDown();
						releaseFirst.await(5, TimeUnit.SECONDS);
					} else {
						secondDone.countDown();
					}
					onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					onError.accept("interrupted");
				} finally {
					concurrent.decrementAndGet();
				}
			}
		};

		AudioAnalysisOrchestrator orchestrator = new AudioAnalysisOrchestrator(blockingAnalyzer);
		try {
			orchestrator.submit(
				"task-1",
				Path.of("first.mp3"),
				AnalysisOptions.withDemucs(false),
				(step, pct) -> {},
				beatmap -> {},
				error -> {},
				null,
				null
			);
			assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

			orchestrator.submit(
				"task-2",
				Path.of("second.mp3"),
				AnalysisOptions.withDemucs(false),
				(step, pct) -> {},
				beatmap -> {},
				error -> {},
				null,
				null
			);

			assertFalse(secondDone.await(200, TimeUnit.MILLISECONDS));
			releaseFirst.countDown();
			assertTrue(secondDone.await(5, TimeUnit.SECONDS));
			assertEquals(1, maxConcurrent.get());
		} finally {
			orchestrator.shutdown();
		}
	}

	@Test
	void cancelByTaskIdStopsRunningAnalysis() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean completed = new AtomicBoolean();

		IAudioAnalyzer longRunningAnalyzer = new IAudioAnalyzer() {
			@Override
			public String backendId() {
				return "long";
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
				try {
					Thread.sleep(10_000);
					onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
					completed.set(true);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					onError.accept("interrupted");
				}
			}
		};

		AudioAnalysisOrchestrator orchestrator = new AudioAnalysisOrchestrator(longRunningAnalyzer);
		try {
			orchestrator.submit(
				"cancel-me",
				Path.of("song.mp3"),
				AnalysisOptions.withDemucs(false),
				(step, pct) -> {},
				beatmap -> completed.set(true),
				error -> {},
				null,
				null
			);
			assertTrue(started.await(5, TimeUnit.SECONDS));
			assertEquals(1, orchestrator.activeTaskCount());
			assertTrue(orchestrator.cancel("cancel-me"));
			assertEquals(0, orchestrator.activeTaskCount());
			assertFalse(completed.get());
		} finally {
			orchestrator.shutdown();
		}
	}
}
