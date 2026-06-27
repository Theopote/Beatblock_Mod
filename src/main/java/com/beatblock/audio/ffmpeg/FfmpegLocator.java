package com.beatblock.audio.ffmpeg;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 统一 ffmpeg 可执行文件路径解析与版本探测。
 * <p>
 * 查找顺序：{@code config/beatblock/ffmpeg_path.txt} → 游戏目录固定候选 → 游戏目录子文件夹递归搜索 → PATH 上的 {@code ffmpeg}。
 */
public final class FfmpegLocator {

	private static final int VERSION_PROBE_TIMEOUT_SEC = 3;
	private static final int MAX_GAME_DIR_SEARCH_DEPTH = 4;

	@FunctionalInterface
	interface ExecutableProbe {
		boolean isExecutable(String executable);
	}

	private FfmpegLocator() {}

	public static String resolveExecutable() {
		return resolveExecutable(
			FabricLoader.getInstance().getConfigDir(),
			FabricLoader.getInstance().getGameDir(),
			FfmpegLocator::isExecutable
		);
	}

	static String resolveExecutable(Path configDir, Path gameDir, ExecutableProbe probe) {
		Path configPath = configDir.resolve("beatblock/ffmpeg_path.txt");
		if (Files.exists(configPath)) {
			try {
				String txt = Files.readString(configPath).trim();
				if (!txt.isEmpty() && probe.isExecutable(txt)) {
					return txt;
				}
			} catch (IOException e) {
				org.slf4j.LoggerFactory.getLogger(FfmpegLocator.class)
					.debug("Unable to read ffmpeg_path.txt", e);
			}
		}

		for (Path candidate : collectGameDirCandidates(gameDir)) {
			if (Files.isRegularFile(candidate)) {
				String absolute = candidate.toAbsolutePath().toString();
				if (probe.isExecutable(absolute)) {
					return absolute;
				}
			}
		}

		if (probe.isExecutable("ffmpeg")) {
			return "ffmpeg";
		}
		return null;
	}

	static List<Path> collectGameDirCandidates(Path gameDir) {
		Set<Path> candidates = new LinkedHashSet<>();
		if (gameDir == null) {
			return List.of();
		}

		List<Path> fixed = List.of(
			gameDir.resolve("ffmpeg.exe"),
			gameDir.resolve("ffmpeg"),
			gameDir.resolve("ffmpeg/bin/ffmpeg.exe"),
			gameDir.resolve("ffmpeg/bin/ffmpeg"),
			gameDir.resolve("bin/ffmpeg.exe"),
			gameDir.resolve("bin/ffmpeg"),
			gameDir.resolve("tools/ffmpeg/ffmpeg.exe"),
			gameDir.resolve("tools/ffmpeg/ffmpeg")
		);
		for (Path path : fixed) {
			candidates.add(path);
		}
		collectRecursiveCandidates(gameDir, 0, candidates);
		return List.copyOf(candidates);
	}

	private static void collectRecursiveCandidates(Path dir, int depth, Set<Path> out) {
		if (dir == null || depth > MAX_GAME_DIR_SEARCH_DEPTH || !Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			for (Path entry : stream.toList()) {
				if (Files.isRegularFile(entry) && isFfmpegFileName(entry.getFileName().toString())) {
					out.add(entry);
				} else if (Files.isDirectory(entry) && shouldDescend(entry)) {
					collectRecursiveCandidates(entry, depth + 1, out);
				}
			}
		} catch (IOException e) {
			org.slf4j.LoggerFactory.getLogger(FfmpegLocator.class)
				.debug("Unable to scan directory for ffmpeg: {}", dir, e);
		}
	}

	private static boolean shouldDescend(Path dir) {
		String name = dir.getFileName() != null ? dir.getFileName().toString().toLowerCase(Locale.ROOT) : "";
		return !name.equals("saves")
			&& !name.equals("logs")
			&& !name.equals("resourcepacks")
			&& !name.equals("shaderpacks")
			&& !name.equals("mods")
			&& !name.equals("assets")
			&& !name.equals(".gradle");
	}

	private static boolean isFfmpegFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return false;
		}
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.equals("ffmpeg.exe") || lower.equals("ffmpeg");
	}

	public static List<String> describeSearchLocations(Path gameDir) {
		List<String> locations = new ArrayList<>();
		locations.add("config/beatblock/ffmpeg_path.txt");
		if (gameDir != null) {
			locations.add(gameDir.resolve("ffmpeg.exe").toString());
			locations.add(gameDir.resolve("ffmpeg/bin/ffmpeg.exe").toString());
			locations.add(gameDir + "/**/ffmpeg.exe (depth " + MAX_GAME_DIR_SEARCH_DEPTH + ")");
		}
		locations.add("PATH: ffmpeg");
		return List.copyOf(locations);
	}

	public static boolean isAvailable() {
		return resolveExecutable() != null;
	}

	public static boolean isExecutable(String executable) {
		try {
			Process process = new ProcessBuilder(executable, "-version")
				.redirectErrorStream(true)
				.start();
			boolean finished = process.waitFor(VERSION_PROBE_TIMEOUT_SEC, TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}
}
