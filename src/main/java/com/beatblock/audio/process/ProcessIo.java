package com.beatblock.audio.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 外部进程 stdout/stderr 读取与输出裁剪。
 */
public final class ProcessIo {

	private ProcessIo() {}

	public static String readProcessOutput(Process process) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) sb.append(line).append('\n');
		}
		return sb.toString();
	}

	public static int waitProcess(Process process) {
		try {
			return process.waitFor();
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
