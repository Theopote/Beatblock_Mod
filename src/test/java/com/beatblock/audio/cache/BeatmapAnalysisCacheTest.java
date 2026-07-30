package com.beatblock.audio.cache;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatmapAnalysisCacheTest {
	@TempDir Path tempDir;

	@Test
	void cleanupRefusesParentTraversal() throws Exception {
		Path cacheDir = Files.createDirectory(tempDir.resolve("cache"));
		Path outside = Files.writeString(tempDir.resolve("outside.wav"), "content");
		BeatmapAnalysisCache.cleanupDemucsStemArtifacts(
			beatmapWithStem("../outside.wav"), cacheDir.resolve("song.beatmap"));
		assertTrue(Files.exists(outside));
	}

	@Test
	void cleanupDeletesStemInsideBeatmapDirectory() throws Exception {
		Path cacheDir = Files.createDirectory(tempDir.resolve("cache"));
		Path stem = Files.writeString(cacheDir.resolve("stem.wav"), "content");
		BeatmapAnalysisCache.cleanupDemucsStemArtifacts(
			beatmapWithStem("stem.wav"), cacheDir.resolve("song.beatmap"));
		assertFalse(Files.exists(stem));
	}

	@Test
	void sameNameDifferentPathsGetDifferentBeatmapPaths() throws Exception {
		Path outputDir = Files.createDirectory(tempDir.resolve("out"));
		Path a = tempDir.resolve("folder-a").resolve("song.mp3");
		Path b = tempDir.resolve("folder-b").resolve("song.mp3");
		Files.createDirectories(a.getParent());
		Files.createDirectories(b.getParent());
		Files.writeString(a, "a");
		Files.writeString(b, "b");

		Path basicA = BeatmapAnalysisCache.buildBeatmapPath(outputDir, a, false);
		Path basicB = BeatmapAnalysisCache.buildBeatmapPath(outputDir, b, false);

		assertFalse(basicA.equals(basicB), "同名不同路径音频不得生成相同缓存文件");
		assertTrue(basicA.getFileName().toString().startsWith("song-"));
		assertTrue(basicB.getFileName().toString().startsWith("song-"));
	}

	private static Beatmap beatmapWithStem(String stemPath) {
		BeatmapMeta meta = new BeatmapMeta(
			"song.wav", 1_000, 120, 1, "4/4", 44_100, "", "3.0.0",
			null, "demucs", Map.of("drums", stemPath));
		return new Beatmap(3, meta, List.of(), List.of(), null, Map.of());
	}
}
