package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Serial audio-analysis scheduler with latest-wins replacement and observable task state. */
public final class AudioAnalysisOrchestrator implements AutoCloseable {

	private final class RegisteredTask {
		final long sequence;
		final @Nullable String taskId;
		final Path audioPath;
		final AnalysisCancelControl control = new AnalysisCancelControl();
		final AtomicReference<AnalysisTaskState> state = new AtomicReference<>(AnalysisTaskState.QUEUED);
		volatile FutureTask<Void> delegate;

		RegisteredTask(long sequence, @Nullable String taskId, Path audioPath) {
			this.sequence = sequence;
			this.taskId = taskId;
			this.audioPath = audioPath;
		}

		boolean cancel() {
			while (true) {
				AnalysisTaskState current = state.get();
				if (current.isTerminal() || current == AnalysisTaskState.CANCELLING) return false;
				AnalysisTaskState next = current == AnalysisTaskState.QUEUED
					? AnalysisTaskState.CANCELLED : AnalysisTaskState.CANCELLING;
				if (!state.compareAndSet(current, next)) continue;
				control.cancelRunningProcess();
				FutureTask<Void> task = delegate;
				if (task != null) {
					task.cancel(true);
					if (next == AnalysisTaskState.CANCELLED) executor.remove(task);
				}
				if (next == AnalysisTaskState.CANCELLED) removeRegistration(this);
				return true;
			}
		}
	}

	private final IAudioAnalyzer analyzer;
	private final ThreadPoolExecutor executor;
	private final MainThreadDispatcher callbackDispatcher;
	private final ConcurrentHashMap<String, RegisteredTask> tasksById = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, RegisteredTask> activeTasks = new ConcurrentHashMap<>();
	private final AtomicLong nextSequence = new AtomicLong();

	public AudioAnalysisOrchestrator(@NonNull IAudioAnalyzer analyzer) {
		this(analyzer, MainThreadDispatcher.immediate());
	}

	public AudioAnalysisOrchestrator(@NonNull IAudioAnalyzer analyzer, @NonNull MainThreadDispatcher callbackDispatcher) {
		this.analyzer = analyzer;
		this.callbackDispatcher = callbackDispatcher;
		this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "beatblock-analyzer");
				thread.setDaemon(true);
				return thread;
			});
	}

	public @NonNull IAudioAnalyzer getAnalyzer() {
		return analyzer;
	}

	public @NonNull Future<?> submit(
		@Nullable String taskId,
		@NonNull Path audioPath,
		@NonNull AnalysisOptions options,
		@Nullable AnalysisProgressCallback onProgress,
		@Nullable Consumer<Beatmap> onComplete,
		@Nullable Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@Nullable Runnable onStarted
	) {
		String normalizedTaskId = normalizeTaskId(taskId);
		RegisteredTask registered = new RegisteredTask(nextSequence.incrementAndGet(), normalizedTaskId, audioPath);
		AtomicBoolean failed = new AtomicBoolean();
		AnalysisProgressCallback progressCallback = onProgress != null
			? (step, pct) -> dispatch(registered.control, () -> onProgress.onProgress(step, pct)) : (step, pct) -> {};
		Consumer<Beatmap> completeCallback = onComplete != null
			? beatmap -> dispatch(registered.control, () -> onComplete.accept(beatmap)) : beatmap -> {};
		Consumer<String> errorCallback = error -> {
			failed.set(true);
			if (onError != null) dispatch(registered.control, () -> onError.accept(error));
		};
		Consumer<AnalysisSummary> summaryCallback = onSummary != null
			? summary -> dispatch(registered.control, () -> onSummary.accept(summary)) : null;

		FutureTask<Void> delegate = new FutureTask<>(() -> {
			if (!registered.state.compareAndSet(AnalysisTaskState.QUEUED, AnalysisTaskState.STARTING)) return null;
			try {
				if (onStarted != null) dispatch(registered.control, onStarted);
				if (!registered.state.compareAndSet(AnalysisTaskState.STARTING, AnalysisTaskState.RUNNING)) return null;
				analyzer.analyze(audioPath, options, progressCallback, completeCallback, errorCallback, summaryCallback,
					registered.control);
			} catch (RuntimeException | Error error) {
				failed.set(true);
				throw error;
			} finally {
				AnalysisTaskState current = registered.state.get();
				AnalysisTaskState terminal = registered.control.isCancelled()
					|| current == AnalysisTaskState.CANCELLING
					? AnalysisTaskState.CANCELLED
					: (failed.get() ? AnalysisTaskState.FAILED : AnalysisTaskState.SUCCEEDED);
				registered.state.set(terminal);
				removeRegistration(registered);
			}
			return null;
		});
		registered.delegate = delegate;
		activeTasks.put(registered.sequence, registered);
		if (normalizedTaskId != null) {
			RegisteredTask previous = tasksById.put(normalizedTaskId, registered);
			if (previous != null) previous.cancel();
		}
		try {
			executor.execute(delegate);
		} catch (RejectedExecutionException error) {
			registered.cancel();
			throw error;
		}
		return cancellableFuture(registered);
	}

	private void dispatch(AnalysisCancelControl control, Runnable action) {
		callbackDispatcher.execute(() -> {
			if (!control.isCancelled()) action.run();
		});
	}

	public boolean cancel(@Nullable String taskId) {
		String normalizedTaskId = normalizeTaskId(taskId);
		if (normalizedTaskId == null) return false;
		RegisteredTask task = tasksById.get(normalizedTaskId);
		return task != null && task.cancel();
	}

	public void cancelAll() {
		for (RegisteredTask task : List.copyOf(activeTasks.values())) task.cancel();
	}

	public int activeTaskCount() {
		return activeTasks.size();
	}

	public @NonNull List<AnalysisTaskSnapshot> taskSnapshots() {
		List<RegisteredTask> ordered = new ArrayList<>(activeTasks.values());
		ordered.sort(Comparator.comparingLong(task -> task.sequence));
		int queuedPosition = 0;
		List<AnalysisTaskSnapshot> snapshots = new ArrayList<>(ordered.size());
		for (RegisteredTask task : ordered) {
			AnalysisTaskState state = task.state.get();
			int position = state == AnalysisTaskState.QUEUED ? ++queuedPosition : 0;
			snapshots.add(new AnalysisTaskSnapshot(task.sequence, task.taskId, task.audioPath, state, position));
		}
		return List.copyOf(snapshots);
	}

	public void shutdown() {
		cancelAll();
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				executor.awaitTermination(2, TimeUnit.SECONDS);
			}
		} catch (InterruptedException error) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void close() {
		shutdown();
	}

	private void removeRegistration(RegisteredTask task) {
		activeTasks.remove(task.sequence, task);
		if (task.taskId != null) tasksById.remove(task.taskId, task);
	}

	private Future<?> cancellableFuture(RegisteredTask registered) {
		return new Future<>() {
			@Override public boolean cancel(boolean mayInterruptIfRunning) { return registered.cancel(); }
			@Override public boolean isCancelled() { return registered.delegate.isCancelled(); }
			@Override public boolean isDone() { return registered.delegate.isDone(); }
			@Override public Object get() throws InterruptedException, ExecutionException { return registered.delegate.get(); }
			@Override public Object get(long timeout, @NonNull TimeUnit unit)
				throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
				return registered.delegate.get(timeout, unit);
			}
		};
	}

	private static @Nullable String normalizeTaskId(@Nullable String taskId) {
		return taskId == null || taskId.isBlank() ? null : taskId;
	}
}
