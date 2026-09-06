package com.beatblock.client.export;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 视频导出原子输出：编码写入唯一临时文件，成功后再替换目标路径；
 * 失败/取消删除临时文件，避免留下半成品 MP4（与 {@code OscProjectStore} / Event Library 一致）。
 */
public final class VideoExportAtomicOutput {

	public static final String TEMP_SUFFIX = ".beatblock-export.tmp";

	private VideoExportAtomicOutput() {}

	/**
	 * 在目标同目录创建唯一临时路径：{@code name.mp4.<random>.beatblock-export.tmp}。
	 * 不创建空内容文件以外的占位（{@link Files#createTempFile} 会创建 0-byte 文件供 ffmpeg {@code -y} 覆盖）。
	 */
	public static Path createTempOutput(Path finalOutput) throws IOException {
		if (finalOutput == null) {
			throw new IOException("export output path is null");
		}
		Path abs = finalOutput.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path fileNamePath = abs.getFileName();
		String fileName = fileNamePath != null ? fileNamePath.toString() : "export.mp4";
		String prefix = fileName + ".";
		if (prefix.length() < 3) {
			prefix = "export." + prefix;
		}
		return parent != null
			? Files.createTempFile(parent, prefix, TEMP_SUFFIX)
			: Files.createTempFile(prefix, TEMP_SUFFIX);
	}

	/** Prefer {@link StandardCopyOption#ATOMIC_MOVE}; fall back when unsupported. */
	public static void promote(Path temporary, Path finalOutput) throws IOException {
		if (temporary == null || finalOutput == null) {
			throw new IOException("export promote paths must not be null");
		}
		Path target = finalOutput.toAbsolutePath().normalize();
		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		moveReplacing(temporary, target);
	}

	static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// 清理失败不应掩盖主异常
		}
	}
}
