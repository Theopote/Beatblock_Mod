package com.beatblock.audio.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 外部进程 stdout/stderr 读取与输出裁剪。
 * <p>
 * 音频分析任务在单线程 executor 上串行调度，任何阻塞 IO 都可能导致取消后的任务
 * 长时间占用工作线程。因此本类提供可中断、可超时的读取/等待方法，并允许调用方
 * 在取消信号到达时尽早返回。
 */
public final class ProcessIo {

	private ProcessIo() {}

	public static String readProcessOutput(Process process) throws IOException {
		return readProcessOutput(process, 5, TimeUnit.MINUTES);
	}

	/**
	 * 在指定时限内读取进程合并后的 stdout/stderr（要求进程已 redirectErrorStream(true)）。
	 * 若超时，则中断读取线程并关闭输入流，避免阻塞工作线程。
	 */
	public static String readProcessOutput(Process process, long timeout, TimeUnit unit) throws IOException {
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
			return task.get(timeout, unit);
		} catch (TimeoutException e) {
			task.cancel(true);
			reader.interrupt();
			try { process.getInputStream().close(); } catch (IOException ignored) {}
			return "";
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
			return waitProcess(process, 5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return -1;
		}
	}

	/**
	 * 等待进程退出，但每 100ms 检查一次，避免无限期阻塞。
	 * 超时后强制 destroy 并再给 5 秒宽限期；若仍未退出则返回 -1。
	 */
	public static int waitProcess(Process process, long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
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
