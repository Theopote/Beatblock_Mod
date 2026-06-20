package com.beatblock.audio.cache;

import com.beatblock.audio.AnalyzerInstaller;
import com.beatblock.audio.beatmap.Beatmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Beatmap / Demucs stem 磁盘缓存路径与兼容性校验。
 */
public final class BeatmapAnalysisCache {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatmapAnalysisCache.class);

	private BeatmapAnalysisCache() {}

	public static int clearBeatmapCacheForAudio(Path audioPath) {
		if (audioPath == null) return 0;
		Path outputDir;
		try {
			outputDir = AnalyzerInstaller.getBeatmapOutputDir();
		} catch (Exception e) {
			LOGGER.warn("BeatBlock AudioAnalysis: cannot resolve beatmap output dir for cache clear reason={}", e.toString());
			return 0;
		}

		Path basic = buildBeatmapPath(outputDir, audioPath, false);
		Path demucs = buildBeatmapPath(outputDir, audioPath, true);
		int removed = 0;
		removed += deleteIfExists(basic);
		removed += deleteIfExists(demucs);
		return removed;
	}

	public static int clearAllAnalysisCacheForAudio(Path audioPath) {
		if (audioPath == null) return 0;
		int removed = clearBeatmapCacheForAudio(audioPath);
		removed += deleteStemCacheForAudio(audioPath);
		return removed;
	}

	public static Path buildBeatmapPath(Path outputDir, Path audioPath, boolean demucsMode) {
		String fileName = audioPath != null && audioPath.getFileName() != null
			? audioPath.getFileName().toString()
			: "audio";
		String baseName = sanitizeBeatmapBaseName(fileName.replaceAll("\\.[^.]+$", ""));
		String normalized = audioPath == null
			? "audio"
			: audioPath.toAbsolutePath().normalize().toString().toLowerCase();
		String audioFingerprint = Integer.toHexString(normalized.hashCode());
		String separationTag = demucsMode ? "demucs" : "basic";
		return outputDir.resolve(baseName + "-" + audioFingerprint + "-" + separationTag + ".beatmap");
	}

	public static boolean isBeatmapVersionCompatible(Beatmap beatmap, boolean expectDemucs, Path beatmapPath) {
		if (beatmap == null || beatmap.meta == null) return false;
		String ver = beatmap.meta.analyzerVersion();
		if (ver == null || ver.isBlank()) return false;
		boolean versionOk;
		try {
			int major = Integer.parseInt(ver.split("\\.")[0]);
			versionOk = major >= 3;
		} catch (NumberFormatException e) {
			return false;
		}
		if (!versionOk) return false;

		boolean hasStemSeparation = beatmap.meta.hasStemSeparation();
		if (expectDemucs) {
			return hasStemSeparation && areDemucsStemsCacheCompatible(beatmap, beatmapPath);
		}
		return !hasStemSeparation;
	}

	public static void cleanupDemucsStemArtifacts(Beatmap beatmap, Path beatmapPath) {
		if (beatmap == null || beatmap.meta == null || beatmap.meta.stems() == null || beatmapPath == null) return;
		Path parent = beatmapPath.getParent();
		if (parent == null) return;
		for (Map.Entry<String, String> entry : beatmap.meta.stems().entrySet()) {
			String relPath = entry.getValue();
			if (relPath == null || relPath.isBlank()) continue;
			Path stemPath = parent.resolve(relPath).normalize();
			deleteIfExists(stemPath);
		}
	}

	private static boolean areDemucsStemsCacheCompatible(Beatmap beatmap, Path beatmapPath) {
		if (beatmap == null || beatmap.meta == null || beatmap.meta.stems() == null || beatmapPath == null) {
			return false;
		}
		Path parent = beatmapPath.getParent();
		if (parent == null) return false;

		for (Map.Entry<String, String> entry : beatmap.meta.stems().entrySet()) {
			String stemKey = entry.getKey();
			String relPath = entry.getValue();
			if (relPath == null || relPath.isBlank()) return false;
			Path stemPath = parent.resolve(relPath).normalize();
			try {
				if (!Files.isRegularFile(stemPath) || Files.size(stemPath) <= 44) {
					LOGGER.info("BeatBlock AudioAnalysis: demucs cache stale, missing/short stem key={} path={}", stemKey, stemPath);
					return false;
				}
				AudioSystem.getAudioFileFormat(stemPath.toFile());
			} catch (UnsupportedAudioFileException | IOException ex) {
				LOGGER.info("BeatBlock AudioAnalysis: demucs cache stale, unreadable stem key={} path={} reason={}",
					stemKey, stemPath, ex.getMessage());
				return false;
			}
		}
		return true;
	}

	private static int deleteStemCacheForAudio(Path audioPath) {
		Path outputDir;
		try {
			outputDir = AnalyzerInstaller.getBeatmapOutputDir();
		} catch (Exception e) {
			LOGGER.warn("BeatBlock AudioAnalysis: cannot resolve beatmap output dir for stem cache clear reason={}", e.toString());
			return 0;
		}
		Path stemDir = outputDir.resolve("stems").resolve(stemCacheFingerprint(audioPath));
		if (!Files.exists(stemDir)) return 0;
		try (var walk = Files.walk(stemDir)) {
			return walk.sorted(java.util.Comparator.reverseOrder())
				.mapToInt(BeatmapAnalysisCache::deleteIfExists)
				.sum();
		} catch (IOException e) {
			LOGGER.warn("BeatBlock AudioAnalysis: failed to clear stem cache dir={} reason={}", stemDir, e.toString());
			return 0;
		}
	}

	private static String stemCacheFingerprint(Path audioPath) {
		String normalized = audioPath == null
			? "audio"
			: audioPath.toAbsolutePath().normalize().toString().toLowerCase();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hash = digest.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < Math.min(hash.length, 8); i++) {
				sb.append(String.format("%02x", hash[i]));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(normalized.hashCode());
		}
	}

	private static String sanitizeBeatmapBaseName(String baseName) {
		if (baseName == null || baseName.isBlank()) return "audio";
		String sanitized = baseName.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
		if (sanitized.isBlank()) return "audio";
		return sanitized;
	}

	private static int deleteIfExists(Path p) {
		if (p == null) return 0;
		try {
			return Files.deleteIfExists(p) ? 1 : 0;
		} catch (IOException e) {
			LOGGER.warn("BeatBlock AudioAnalysis: failed to delete cache file={} reason={}", p.getFileName(), e.toString());
			return 0;
		}
	}
}
