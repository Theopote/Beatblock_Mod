package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BooleanSupplier;

class AudioAnalysisOrchestratorTest {

	@Test
	void createForClientRejectsNullDispatcher() {
		IAudioAnalyzer analyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "x"; }
			@Override public boolean isAvailable() { return true; }
			@Override
			public void analyze(
				Path audioPath,
				AnalysisOptions options,
				AnalysisProgressCallback onProgress,
				Consumer<Beatmap> onComplete,
				Consumer<String> onError,
				Consumer<AnalysisSummary> onSummary,
				AnalysisCancelControl control
			) {}
		};
		assertThrows(IllegalArgumentException.class, () ->
			AudioAnalysisOrchestrator.createForClient(analyzer, null));
	}

	private static boolean waitUntil(BooleanSupplier condition, long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline) return false;
			Thread.sleep(10);
		}
		return true;
	}

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

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(blockingAnalyzer);
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

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(longRunningAnalyzer);
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
			assertTrue(waitUntil(() -> orchestrator.activeTaskCount() == 0, 1, TimeUnit.SECONDS),
				"取消后任务应在短时间内从调度器中移除");
			assertEquals(0, orchestrator.activeTaskCount());
			assertFalse(completed.get());
		} finally {
			orchestrator.shutdown();
		}
	}

	@Test
	void finishingPreviousTaskMustNotRemoveReplacementTask() throws Exception {
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch firstExited = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		CountDownLatch releaseSecond = new CountDownLatch(1);

		IAudioAnalyzer analyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "replacement-race"; }
			@Override public boolean isAvailable() { return true; }

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
				boolean first = "first.mp3".equals(String.valueOf(audioPath.getFileName()));
				try {
					if (first) {
						firstStarted.countDown();
						new CountDownLatch(1).await();
					} else {
						secondStarted.countDown();
						releaseSecond.await(5, TimeUnit.SECONDS);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					if (first) firstExited.countDown();
				}
			}
		};

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(analyzer);
		try {
			orchestrator.submit("same-id", Path.of("first.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

			orchestrator.submit("same-id", Path.of("second.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			assertTrue(firstExited.await(5, TimeUnit.SECONDS));
			assertTrue(secondStarted.await(5, TimeUnit.SECONDS));

			assertEquals(1, orchestrator.activeTaskCount());
			assertTrue(orchestrator.cancel("same-id"));
			assertTrue(waitUntil(() -> orchestrator.activeTaskCount() == 0, 1, TimeUnit.SECONDS),
				"取消后替换任务应在短时间内从调度器中移除");
			assertEquals(0, orchestrator.activeTaskCount());
		} finally {
			releaseSecond.countDown();
			orchestrator.shutdown();
		}
	}

	@Test
	void completedTaskDoesNotRemainRegistered() throws Exception {
		IAudioAnalyzer immediateAnalyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "immediate"; }
			@Override public boolean isAvailable() { return true; }

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
				onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
			}
		};

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(immediateAnalyzer);
		try {
			var future = orchestrator.submit("fast", Path.of("fast.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			future.get(5, TimeUnit.SECONDS);
			assertEquals(0, orchestrator.activeTaskCount());
		} finally {
			orchestrator.shutdown();
		}
	}

	@Test
	void dispatchesAllCallbacksThroughConfiguredDispatcher() throws Exception {
		List<Runnable> queuedCallbacks = new ArrayList<>();
		List<String> callbackOrder = new ArrayList<>();
		IAudioAnalyzer callbackAnalyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "callbacks"; }
			@Override public boolean isAvailable() { return true; }

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
				onProgress.onProgress("STEP", 50);
				onSummary.accept(new AnalysisSummary(1, 2, 3, 4, "basic", "test"));
				onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
			}
		};

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForClient(callbackAnalyzer, queuedCallbacks::add);
		try {
			orchestrator.submit(
				"callbacks",
				Path.of("callbacks.mp3"),
				AnalysisOptions.withDemucs(false),
				(step, pct) -> callbackOrder.add("progress"),
				beatmap -> callbackOrder.add("complete"),
				error -> callbackOrder.add("error"),
				summary -> callbackOrder.add("summary"),
				() -> callbackOrder.add("started")
			).get(5, TimeUnit.SECONDS);

			assertTrue(callbackOrder.isEmpty());
			assertEquals(4, queuedCallbacks.size());
			queuedCallbacks.forEach(Runnable::run);
			assertEquals(List.of("started", "progress", "summary", "complete"), callbackOrder);
			Thread worker = Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> "beatblock-analyzer".equals(thread.getName()))
				.findFirst()
				.orElseThrow();
			assertTrue(worker.isDaemon());
		} finally {
			orchestrator.shutdown();
		}
	}

	@Test
	void futureCancelFalseDoesNotInterruptRunningTask() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch released = new CountDownLatch(1);
		AtomicBoolean completed = new AtomicBoolean();

		IAudioAnalyzer analyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "cancel-false-running"; }
			@Override public boolean isAvailable() { return true; }
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
					released.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
				completed.set(true);
			}
		};

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(analyzer);
		try {
			Future<?> future = orchestrator.submit(
				"no-interrupt", Path.of("song.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			assertTrue(started.await(5, TimeUnit.SECONDS));
			assertFalse(future.cancel(false));
			assertEquals(1, orchestrator.activeTaskCount());
			released.countDown();
			assertTrue(waitUntil(() -> orchestrator.activeTaskCount() == 0, 2, TimeUnit.SECONDS));
			assertTrue(completed.get());
		} finally {
			orchestrator.shutdown();
		}
	}

	@Test
	void futureCancelFalseRemovesQueuedTask() throws Exception {
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		IAudioAnalyzer analyzer = new IAudioAnalyzer() {
			@Override public String backendId() { return "cancel-false-queued"; }
			@Override public boolean isAvailable() { return true; }
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
				try {
					if ("first.mp3".equals(String.valueOf(audioPath.getFileName()))) {
						firstStarted.countDown();
						releaseFirst.await(5, TimeUnit.SECONDS);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				onComplete.accept(new Beatmap(1, null, List.of(), List.of(), null, null));
			}
		};

		AudioAnalysisOrchestrator orchestrator = AudioAnalysisOrchestrator.createForTesting(analyzer);
		try {
			orchestrator.submit(
				"task-1", Path.of("first.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

			Future<?> queued = orchestrator.submit(
				"task-2", Path.of("second.mp3"), AnalysisOptions.withDemucs(false),
				(step, pct) -> {}, beatmap -> {}, error -> {}, null, null);
			assertEquals(2, orchestrator.activeTaskCount());

			assertTrue(queued.cancel(false));
			assertTrue(waitUntil(() -> orchestrator.activeTaskCount() == 1, 1, TimeUnit.SECONDS));
			assertEquals(1, orchestrator.activeTaskCount());
		} finally {
			releaseFirst.countDown();
			orchestrator.shutdown();
		}
	}
}
