package com.beatblock.video;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 视频导出服务：在客户端主线程驱动 {@link com.beatblock.client.export.VideoExportCoordinator}。
 *
 * <p>生产必须通过 {@link #createForClient} 注入主线程 executor；
 * 测试使用 {@link #createForTesting()}。禁止 {@code null} 静默回退为 {@code Runnable::run}。
 */
public final class VideoExportService {

	private final Consumer<Runnable> clientExecutor;
	private @Nullable VideoExportProgress activeProgress;
	private @Nullable VideoExportResult lastResult;
	private @Nullable Consumer<VideoExportProgress> progressListener;
	private @Nullable Consumer<VideoExportResult> completionListener;

	/**
	 * 客户端生产入口：导出进度/完成回调必须调度到主线程
	 * （例如 {@code ClientThreadExecutor::run}）。
	 */
	public static VideoExportService createForClient(Consumer<Runnable> clientExecutor) {
		if (clientExecutor == null) {
			throw new IllegalArgumentException("clientExecutor must not be null");
		}
		return new VideoExportService(clientExecutor);
	}

	/**
	 * 测试入口：回调在调用线程直接执行。请勿在生产路径调用。
	 */
	public static VideoExportService createForTesting() {
		return new VideoExportService(Runnable::run);
	}

	private VideoExportService(Consumer<Runnable> clientExecutor) {
		this.clientExecutor = clientExecutor;
	}

	public boolean isExporting() {
		if (activeProgress == null) {
			return false;
		}
		return switch (activeProgress.state()) {
			case STARTING, RUNNING, FINALIZING -> true;
			default -> false;
		};
	}

	public @Nullable VideoExportProgress activeProgress() {
		return activeProgress;
	}

	public @Nullable VideoExportResult lastResult() {
		return lastResult;
	}

	public void clearLastResult() {
		lastResult = null;
	}

	public void setProgressListener(@Nullable Consumer<VideoExportProgress> listener) {
		this.progressListener = listener;
	}

	public void setCompletionListener(@Nullable Consumer<VideoExportResult> listener) {
		this.completionListener = listener;
	}

	public boolean startExport(VideoExportSettings settings) {
		if (settings == null || isExporting()) {
			return false;
		}
		lastResult = null;
		activeProgress = VideoExportProgress.starting(settings);
		emitProgress();
		clientExecutor.accept(() -> com.beatblock.client.export.VideoExportCoordinator.getInstance().start(settings, this));
		return true;
	}

	public void cancelExport() {
		if (!isExporting()) {
			return;
		}
		clientExecutor.accept(() -> com.beatblock.client.export.VideoExportCoordinator.getInstance().cancel());
	}

	public void onProgressUpdated(VideoExportProgress progress) {
		activeProgress = progress;
		emitProgress();
	}

	public void onCompleted(VideoExportResult result) {
		lastResult = result;
		activeProgress = result.progress();
		emitProgress();
		if (completionListener != null) {
			completionListener.accept(result);
		}
		activeProgress = null;
	}

	private void emitProgress() {
		if (progressListener != null && activeProgress != null) {
			progressListener.accept(activeProgress);
		}
	}

	public record VideoExportResult(
		boolean success,
		@Nullable Path outputPath,
		String message,
		VideoExportProgress progress
	) {}
}
