package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 统一调度音频分析任务的生命周期：单线程串行执行、按 ID 取消、关闭时清理。
 */
public final class AudioAnalysisOrchestrator implements AutoCloseable {

	private static final class RegisteredTask {
		final AnalysisCancelControl control;
		final Future<?> future;

		RegisteredTask(AnalysisCancelControl control, Future<?> future) {
			this.control = control;
			this.future = future;
		}

		boolean cancel() {
			control.cancelRunningProcess();
			return future.cancel(true);
		}
	}

	private final IAudioAnalyzer analyzer;
	private final ExecutorService executor;
	private final MainThreadDispatcher callbackDispatcher;
	private final ConcurrentHashMap<String, RegisteredTask> tasksById = new ConcurrentHashMap<>();

	public AudioAnalysisOrchestrator(@NonNull IAudioAnalyzer analyzer) {
		this(analyzer, MainThreadDispatcher.immediate());
	}

	public AudioAnalysisOrchestrator(
		@NonNull IAudioAnalyzer analyzer,
		@NonNull MainThreadDispatcher callbackDispatcher
	) {
		this.analyzer = analyzer;
		this.callbackDispatcher = callbackDispatcher;
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "beatblock-analyzer");
			t.setDaemon(false);
			return t;
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
		AnalysisCancelControl control = new AnalysisCancelControl();
		@Nullable String normalizedTaskId = normalizeTaskId(taskId);
		AnalysisProgressCallback progressCallback = onProgress != null
			? (step, pct) -> dispatch(control, () -> onProgress.onProgress(step, pct))
			: (step, pct) -> {};
		Consumer<Beatmap> completeCallback = onComplete != null
			? beatmap -> dispatch(control, () -> onComplete.accept(beatmap))
			: beatmap -> {};
		Consumer<String> errorCallback = onError != null
			? error -> dispatch(control, () -> onError.accept(error))
			: error -> {};
		Consumer<AnalysisSummary> summaryCallback = onSummary != null
			? summary -> dispatch(control, () -> onSummary.accept(summary))
			: null;

		AtomicReference<RegisteredTask> taskRef = new AtomicReference<>();
		FutureTask<Void> delegate = new FutureTask<>(() -> {
			try {
				if (onStarted != null) {
					dispatch(control, onStarted);
				}
				analyzer.analyze(
					audioPath,
					options,
					progressCallback,
					completeCallback,
					errorCallback,
					summaryCallback,
					control
				);
			} finally {
				if (normalizedTaskId != null) {
					tasksById.remove(normalizedTaskId, taskRef.get());
				}
			}
			return null;
		});

		Future<?> wrapped = wrapCancelableFuture(delegate, control);
		RegisteredTask registeredTask = new RegisteredTask(control, wrapped);
		taskRef.set(registeredTask);
		if (normalizedTaskId != null) {
			RegisteredTask previous = tasksById.put(normalizedTaskId, registeredTask);
			if (previous != null) {
				previous.cancel();
			}
		}
		try {
			executor.execute(delegate);
		} catch (RejectedExecutionException error) {
			if (normalizedTaskId != null) {
				tasksById.remove(normalizedTaskId, registeredTask);
			}
			registeredTask.cancel();
			throw error;
		}
		return wrapped;
	}

	private void dispatch(AnalysisCancelControl control, Runnable action) {
		callbackDispatcher.execute(() -> {
			if (!control.isCancelled()) {
				action.run();
			}
		});
	}

	public boolean cancel(@Nullable String taskId) {
		String normalizedTaskId = normalizeTaskId(taskId);
		if (normalizedTaskId == null) {
			return false;
		}
		RegisteredTask task = tasksById.remove(normalizedTaskId);
		if (task == null) {
			return false;
		}
		return task.cancel();
	}

	public void cancelAll() {
		for (RegisteredTask task : tasksById.values()) {
			task.cancel();
		}
		tasksById.clear();
	}

	public int activeTaskCount() {
		return tasksById.size();
	}

	public void shutdown() {
		cancelAll();
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				executor.awaitTermination(2, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void close() {
		shutdown();
	}

	private static @Nullable String normalizeTaskId(@Nullable String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return null;
		}
		return taskId;
	}

	private static Future<?> wrapCancelableFuture(Future<?> delegate, AnalysisCancelControl control) {
		return new Future<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				control.cancelRunningProcess();
				return delegate.cancel(true);
			}

			@Override
			public boolean isCancelled() {
				return delegate.isCancelled();
			}

			@Override
			public boolean isDone() {
				return delegate.isDone();
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return delegate.get();
			}

			@Override
			public Object get(long timeout, @NonNull TimeUnit unit)
				throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
				return delegate.get(timeout, unit);
			}
		};
	}
}
