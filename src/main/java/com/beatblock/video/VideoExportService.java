package com.beatblock.video;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 视频导出服务：在客户端主线程驱动 {@link com.beatblock.client.export.VideoExportCoordinator}。
 */
public final class VideoExportService {

	private final Consumer<Runnable> clientExecutor;
	private @Nullable VideoExportProgress activeProgress;
	private @Nullable Consumer<VideoExportProgress> progressListener;
	private @Nullable Consumer<VideoExportResult> completionListener;

	public VideoExportService(Consumer<Runnable> clientExecutor) {
		this.clientExecutor = clientExecutor != null ? clientExecutor : Runnable::run;
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
