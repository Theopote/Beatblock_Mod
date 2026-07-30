package com.beatblock.audio;

import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.ffmpeg.FfmpegTranscodeOutcome;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 音频转换服务：后台调用 {@link FfmpegService} 将不支持格式转换为 MP3。
 * <p>
 * 关闭时会主动取消当前运行的 ffmpeg 子进程并清理未完成的输出文件。
 */
public final class AudioConversionService implements AutoCloseable {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-audio-converter");
		t.setDaemon(true);
		return t;
	});
	private final MainThreadDispatcher callbackDispatcher;
	private final AtomicReference<AudioConversionCancelControl> activeControl = new AtomicReference<>();

	public AudioConversionService() {
		this(MainThreadDispatcher.immediate());
	}

	public AudioConversionService(@NonNull MainThreadDispatcher callbackDispatcher) {
		this.callbackDispatcher = callbackDispatcher;
	}

	public @NonNull Future<?> convertToMp3Async(
		@NonNull Path inputAudio,
		@Nullable ProgressCallback onProgress,
		@NonNull Consumer<Path> onComplete,
		@NonNull Consumer<String> onError
	) {
		return executor.submit(() -> convertToMp3(inputAudio, onProgress, onComplete, onError));
	}

	private void convertToMp3(
		Path inputAudio,
		@Nullable ProgressCallback onProgress,
		Consumer<Path> onComplete,
		Consumer<String> onError
	) {
		AudioConversionCancelControl control = new AudioConversionCancelControl();
		if (!activeControl.compareAndSet(null, control)) {
			// 理论上单线程 executor 不会并发，但做防御性处理。
			onError.accept("已有转换任务正在进行。");
			return;
		}
		try {
			Path fallbackDir = FabricLoader.getInstance().getGameDir();
			var outcome = FfmpegService.transcodeToMp3(
				inputAudio,
				fallbackDir,
				control,
				onProgress != null
					? (message, percent) -> dispatch(() -> onProgress.accept(message, percent))
					: (message, percent) -> {}
			);

			if (control.isCancelled()) {
				return;
			}
			if (outcome instanceof FfmpegTranscodeOutcome.AlreadyMp3 already) {
				if (onProgress != null) {
					dispatch(() -> onProgress.accept("源文件已是 MP3，跳过转换。", 100));
				}
				dispatch(() -> onComplete.accept(already.path()));
			} else if (outcome instanceof FfmpegTranscodeOutcome.Success success) {
				if (onProgress != null) {
					dispatch(() -> onProgress.accept("转换完成。", 100));
				}
				dispatch(() -> onComplete.accept(success.outputPath()));
			} else if (outcome instanceof FfmpegTranscodeOutcome.Failure failure) {
				dispatch(() -> onError.accept(failure.message()));
			}
		} finally {
			activeControl.compareAndSet(control, null);
		}
	}

	private void dispatch(Runnable action) {
		callbackDispatcher.execute(action);
	}

	public void shutdown() {
		AudioConversionCancelControl control = activeControl.get();
		if (control != null) {
			control.cancel();
		}
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
					com.beatblock.BeatBlock.LOGGER.warn("BeatBlock AudioConversionService: executor did not terminate cleanly");
				}
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

	@FunctionalInterface
	public interface ProgressCallback {
		void accept(String message, int percent);
	}
}
