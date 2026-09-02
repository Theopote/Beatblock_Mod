package com.beatblock.audio.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部进程 stdout/stderr 读取与输出裁剪。
 * <p>
 * 音频分析任务在单线程 executor 上串行调度，任何阻塞 IO 都可能导致取消后的任务
 * 长时间占用工作线程。因此本类提供可中断、可超时的读取/等待方法，并允许调用方
 * 在取消信号到达时尽早返回。
 */
public final class ProcessIo {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessIo.class);

	private ProcessIo() {}

	/** Best-effort stream close while aborting a blocked process read. */
	public static void closeQuietly(InputStream stream) {
		if (stream == null) {
			return;
		}
		try {
			stream.close();
		} catch (IOException closeFailure) {
			LOGGER.trace("Failed to close process stream during cancel", closeFailure);
		}
	}

	public static String readProcessOutput(Process process) throws IOException {
		return readProcessOutput(process, 5, TimeUnit.MINUTES);
	}

	/**
	 * 在指定时限内读取进程合并后的 stdout/stderr（要求进程已 redirectErrorStream(true)）。
	 * 若超时，则中断读取线程并关闭输入流，避免阻塞工作线程。
	 */
	public static String readProcessOutput(Process process, long timeout, TimeUnit unit) throws IOException {
		return readProcessOutput(process, timeout, unit, null);
	}

	/**
	 * 在指定时限内读取进程输出，并响应外部取消信号。
	 */
	public static String readProcessOutput(Process process, long timeout, TimeUnit unit,
		com.beatblock.audio.AnalysisCancelControl control) throws IOException {
		FutureTask<String> task = new FutureTask<>(() -> {
			StringBuilder sb = new StringBuilder();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = br.readLine()) != null) sb.append(line).append('\n');
			}
			return sb.toString();
		});
		Thread reader = new Thread(task, "beatblock-process-io-reader");
		reader.setDaemon(true);
		reader.start();
		try {
			long slice = Math.min(100, unit.toMillis(timeout));
			long deadline = System.nanoTime() + unit.toNanos(timeout);
			while (!task.isDone()) {
				try {
					return task.get(slice, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					if (control != null && control.isCancelled()) {
						task.cancel(true);
						reader.interrupt();
						closeQuietly(process.getInputStream());
						return "";
					}
					if (System.nanoTime() >= deadline) {
						task.cancel(true);
						reader.interrupt();
						closeQuietly(process.getInputStream());
						return "";
					}
					if (Thread.currentThread().isInterrupted()) {
						task.cancel(true);
						reader.interrupt();
						closeQuietly(process.getInputStream());
						throw new InterruptedException();
					}
				}
			}
			return task.get();
		} catch (InterruptedException e) {
			task.cancel(true);
			reader.interrupt();
			Thread.currentThread().interrupt();
			return "";
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			throw new IOException(cause);
		}
	}

	public static int waitProcess(Process process) {
		try {
			return waitProcess(process, 5, TimeUnit.MINUTES, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return -1;
		}
	}

	public static int waitProcess(Process process, long timeout, TimeUnit unit) throws InterruptedException {
		return waitProcess(process, timeout, unit, null);
	}

	/**
	 * 等待进程退出，但每 100ms 检查一次，避免无限期阻塞。
	 * 超时后强制 destroy 并再给 5 秒宽限期；若仍未退出则返回 -1。
	 */
	public static int waitProcess(Process process, long timeout, TimeUnit unit,
		com.beatblock.audio.AnalysisCancelControl control) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
			if (control != null && control.isCancelled()) {
				process.destroyForcibly();
			}
			if (Thread.currentThread().isInterrupted()) {
				process.destroyForcibly();
				throw new InterruptedException();
			}
			if (System.nanoTime() >= deadline) {
				process.destroyForcibly();
				if (!process.waitFor(5, TimeUnit.SECONDS)) {
					return -1;
				}
			}
		}
		return process.exitValue();
	}

	public static int waitProcessCancellable(Process process, long timeout, TimeUnit unit,
		com.beatblock.audio.AnalysisCancelControl control) {
		try {
			return waitProcess(process, timeout, unit, control);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return -1;
		}
	}

	public static String sanitizeProcessOutput(String raw) {
		if (raw == null) return "";
		String text = raw.trim();
		if (text.isEmpty()) return "";
		if (text.length() <= 1200) return text;
		return text.substring(text.length() - 1200);
	}

	public static String consumeLines(InputStream input) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	public static String rootMessage(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		if (root.getMessage() != null && !root.getMessage().isBlank()) {
			return root.getMessage();
		}
		return root.getClass().getSimpleName();
	}
}
