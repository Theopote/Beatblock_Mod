package com.beatblock.audio.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FfmpegLocatorTest {

	@TempDir
	Path tempRoot;

	@Test
	void configPathTakesPriorityOverGameDir() throws Exception {
		Path configDir = tempRoot.resolve("config");
		Path gameDir = tempRoot.resolve("game");
		Files.createDirectories(configDir.resolve("beatblock"));
		Files.createDirectories(gameDir);

		Path customExe = tempRoot.resolve("custom-ffmpeg.exe");
		Files.writeString(configDir.resolve("beatblock/ffmpeg_path.txt"), customExe.toString());
		Path gameExe = gameDir.resolve("ffmpeg.exe");
		Files.writeString(gameExe, "placeholder");

		String resolved = FfmpegLocator.resolveExecutable(configDir, gameDir, exe -> exe.equals(customExe.toString()));
		assertEquals(customExe.toString(), resolved);
	}

	@Test
	void fallsBackToGameDirCandidate() throws Exception {
		Path configDir = tempRoot.resolve("config");
		Path gameDir = tempRoot.resolve("game");
		Files.createDirectories(configDir.resolve("beatblock"));
		Files.createDirectories(gameDir);

		Path gameExe = gameDir.resolve("ffmpeg.exe");
		Files.writeString(gameExe, "placeholder");
		String gameExePath = gameExe.toAbsolutePath().toString();

		String resolved = FfmpegLocator.resolveExecutable(configDir, gameDir, exe -> exe.equals(gameExePath));
		assertEquals(gameExePath, resolved);
	}

	@Test
	void skipsInvalidConfigAndUsesPathFallback() throws Exception {
		Path configDir = tempRoot.resolve("config");
		Path gameDir = tempRoot.resolve("game");
		Files.createDirectories(configDir.resolve("beatblock"));
		Files.createDirectories(gameDir);
		Files.writeString(configDir.resolve("beatblock/ffmpeg_path.txt"), "missing-ffmpeg.exe");

		String resolved = FfmpegLocator.resolveExecutable(configDir, gameDir, exe -> "ffmpeg".equals(exe));
		assertEquals("ffmpeg", resolved);
	}

	@Test
	void returnsNullWhenNothingMatches() throws Exception {
		Path configDir = tempRoot.resolve("config");
		Path gameDir = tempRoot.resolve("game");
		Files.createDirectories(configDir.resolve("beatblock"));
		Files.createDirectories(gameDir);

		assertNull(FfmpegLocator.resolveExecutable(configDir, gameDir, exe -> false));
	}
}
