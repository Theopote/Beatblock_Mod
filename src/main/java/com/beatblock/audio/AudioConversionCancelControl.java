package com.beatblock.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 音频转换任务取消控制器：与 Python 分析器 {@link AnalysisCancelControl} 类似，
 * 负责跟踪 ffmpeg 子进程并在取消/关闭时强制终止，同时清理未完成的输出文件。
 */
public final class AudioConversionCancelControl {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioConversionCancelControl.class);

	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final AtomicReference<Process> activeProcess = new AtomicReference<>();
	private final AtomicReference<Path> outputPath = new AtomicReference<>();

	public void attachProcess(Process process) {
		if (process == null) return;
		activeProcess.set(process);
		if (cancelled.get()) {
			destroy(process);
		}
	}

	public void clearProcess(Process process) {
		activeProcess.compareAndSet(process, null);
	}

	public void markOutputPath(Path path) {
		outputPath.set(path);
	}

	public void clearOutputPath() {
		outputPath.set(null);
	}

	public boolean isCancelled() {
		return cancelled.get();
	}

	public void cancel() {
		if (!cancelled.compareAndSet(false, true)) return;
		Process process = activeProcess.getAndSet(null);
		if (process != null) {
			destroy(process);
		}
		Path path = outputPath.getAndSet(null);
		if (path != null) {
			cleanup(path);
		}
	}

	private static void destroy(Process process) {
		process.destroy();
		try {
			if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}
	}

	private static void cleanup(Path path) {
		try {
			Files.deleteIfExists(path);
			LOGGER.debug("BeatBlock AudioConversion: 已删除取消时产生的部分输出文件 {}", path);
		} catch (IOException e) {
			LOGGER.debug("BeatBlock AudioConversion: 删除部分输出文件失败 {}", path, e);
		}
	}
}
