package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一调度音频分析任务的生命周期：单线程串行执行、按 ID 取消、关闭时清理。
 */
public final class AudioAnalysisOrchestrator {

	private static final class RegisteredTask {
		final AnalysisCancelControl control;
		final Future<?> future;

		RegisteredTask(AnalysisCancelControl control, Future<?> future) {
			this.control = control;
			this.future = future;
		}
	}

	private final IAudioAnalyzer analyzer;
	private final ExecutorService executor;
	private final ConcurrentHashMap<String, RegisteredTask> tasksById = new ConcurrentHashMap<>();

	public AudioAnalysisOrchestrator(IAudioAnalyzer analyzer) {
		this.analyzer = analyzer;
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "beatblock-analyzer");
			t.setDaemon(true);
			return t;
		});
	}

	public IAudioAnalyzer getAnalyzer() {
		return analyzer;
	}

	public Future<?> submit(
		String taskId,
		Path audioPath,
		AnalysisOptions options,
		AnalysisProgressCallback onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted
	) {
		AnalysisCancelControl control = new AnalysisCancelControl();
		String normalizedTaskId = normalizeTaskId(taskId);

		Future<?> delegate = executor.submit(() -> {
			try {
				if (onStarted != null) {
					onStarted.run();
				}
				analyzer.analyze(
					audioPath,
					options,
					onProgress,
					onComplete,
					onError,
					onSummary,
					control
				);
			} finally {
				if (normalizedTaskId != null) {
					tasksById.remove(normalizedTaskId);
				}
			}
		});

		Future<?> wrapped = wrapCancelableFuture(delegate, control);
		if (normalizedTaskId != null) {
			RegisteredTask previous = tasksById.put(normalizedTaskId, new RegisteredTask(control, wrapped));
			if (previous != null) {
				previous.control.cancelRunningProcess();
				previous.future.cancel(true);
			}
		}
		return wrapped;
	}

	public boolean cancel(String taskId) {
		String normalizedTaskId = normalizeTaskId(taskId);
		if (normalizedTaskId == null) {
			return false;
		}
		RegisteredTask task = tasksById.remove(normalizedTaskId);
		if (task == null) {
			return false;
		}
		task.control.cancelRunningProcess();
		return task.future.cancel(true);
	}

	public void cancelAll() {
		for (RegisteredTask task : tasksById.values()) {
			task.control.cancelRunningProcess();
			task.future.cancel(true);
		}
		tasksById.clear();
	}

	public int activeTaskCount() {
		return tasksById.size();
	}

	public void shutdown() {
		cancelAll();
		executor.shutdownNow();
	}

	private static String normalizeTaskId(String taskId) {
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
