package com.beatblock.ui.preferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic config file replace (temp → ATOMIC_MOVE / fallback). */
final class AtomicConfigFiles {

	private AtomicConfigFiles() {}

	static void writeAtomically(Path target, String content) throws IOException {
		if (target == null) {
			throw new IOException("config path is null");
		}
		Path abs = target.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path fileNamePath = abs.getFileName();
		String fileName = fileNamePath != null ? fileNamePath.toString() : "config.json";
		Path temp = null;
		try {
			temp = parent != null
				? Files.createTempFile(parent, fileName + ".", ".tmp")
				: Files.createTempFile(fileName + ".", ".tmp");
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			try {
				Files.move(temp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temp, abs, StandardCopyOption.REPLACE_EXISTING);
			}
			temp = null;
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// cleanup must not mask primary failure
				}
			}
		}
	}
}
