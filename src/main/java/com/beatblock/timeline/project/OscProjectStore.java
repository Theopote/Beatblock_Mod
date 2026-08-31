package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.MarkerType;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.BuildLayerPersistence;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * .osc 项目文件读写（轻量版）。
 *
 * 当前版本仅存储项目身份与时间线基础信息：
 * - projectId / projectPath
 * - timelineName
 * - audioPath
 */
public final class OscProjectStore {

	private static final int CURRENT_VERSION = 4;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private OscProjectStore() {}

	public static void save(Path filePath, Timeline timeline) throws IOException {
		save(filePath, timeline, null);
	}

	public static void save(Path filePath, Timeline timeline, @Nullable BuildLayerManager layerManager) throws IOException {
		if (filePath == null) throw new IOException("保存失败：文件路径为空");
		if (timeline == null) throw new IOException("保存失败：Timeline 为空");

		Path abs = filePath.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) Files.createDirectories(parent);

		String projectId = stringMeta(timeline, "projectId");
		if (projectId.isBlank()) projectId = UUID.randomUUID().toString();
		String audioPath = stringMeta(timeline, "audioPath");
		String timelineName = timeline.getName() == null ? "" : timeline.getName();
		JsonObject root = new JsonObject();
		root.addProperty("version", CURRENT_VERSION);
		root.addProperty("projectId", projectId);
		root.addProperty("projectPath", abs.toString());
		root.addProperty("timelineName", timelineName);
		root.addProperty("audioPath", audioPath);
		root.addProperty("durationSeconds", timeline.getDurationSeconds());
		root.addProperty("bpm", timeline.getBpm());
		JsonArray markers = new JsonArray();
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null) continue;
			JsonObject markerObj = new JsonObject();
			markerObj.addProperty("id", marker.getId());
			markerObj.addProperty("timeSeconds", marker.getTimeSeconds());
			markerObj.addProperty("name", marker.getName());
			markerObj.addProperty("type", marker.getType().name());
			markers.add(markerObj);
		}
		root.add("markers", markers);
		if (layerManager != null) {
			root.add("buildLayers", BuildLayerPersistence.toJson(layerManager));
			root.add("buildLayerGroups", BuildLayerPersistence.groupsToJson(layerManager));
		}
		root.add("animationTracks", TimelineAnimationPersistence.toJson(timeline));
		JsonObject choreography = ChoreographyPlanPersistence.toJson(timeline);
		if (choreography != null) root.add("choreography", choreography);

		// 原子写入：唯一临时文件 + ATOMIC_MOVE（必要时回退）+ 失败清理，避免半写/冲突。
		String json = GSON.toJson(root);
		writeAtomically(abs, json);

		// 回写到 timeline，确保后续 UI 隔离键稳定。
		timeline.setMetadata("projectId", projectId);
		timeline.setMetadata("projectPath", abs.toString());
		if (!audioPath.isBlank()) timeline.setMetadata("audioPath", audioPath);
	}

	/**
	 * Write {@code content} to {@code target} via a unique sibling temp file, then replace.
	 * Prefers {@link StandardCopyOption#ATOMIC_MOVE}; falls back when the filesystem rejects it.
	 * Temp files are always cleaned up on failure (and no longer exist after a successful move).
	 */
	static void writeAtomically(Path target, String content) throws IOException {
		if (target == null) throw new IOException("保存失败：目标路径为空");
		Path abs = target.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		Path fileNamePath = abs.getFileName();
		String fileName = fileNamePath != null ? fileNamePath.toString() : "project.osc";
		Path temp = null;
		try {
			// 唯一临时名，避免并发保存争用固定的 "*.osc.tmp"
			temp = parent != null
				? Files.createTempFile(parent, fileName + ".", ".tmp")
				: Files.createTempFile(fileName + ".", ".tmp");
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			moveReplacing(temp, abs);
			temp = null; // 已成功移走，finally 无需再删
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// 清理失败不应掩盖主异常
				}
			}
		}
	}

	/** Prefer atomic replace; fall back when the filesystem cannot do ATOMIC_MOVE. */
	static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static LoadedProject load(Path filePath) throws IOException {
		return load(filePath, null);
	}

	public static LoadedProject load(Path filePath, @Nullable BuildLayerManager layerManager) throws IOException {
		return load(filePath, layerManager, null);
	}

	public static LoadedProject load(Path filePath, @Nullable BuildLayerManager layerManager, @Nullable Timeline timeline) throws IOException {
		if (filePath == null) throw new IOException("打开失败：文件路径为空");
		Path abs = filePath.toAbsolutePath().normalize();
		if (!Files.exists(abs)) throw new IOException("打开失败：文件不存在 " + abs);

		String json = Files.readString(abs, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		int version = getInt(root, "version", 1);
		if (version > CURRENT_VERSION) {
			throw new IOException("不支持的 .osc 版本: " + version + " (当前支持 <= " + CURRENT_VERSION + ")");
		}

		String projectId = getString(root, "projectId", "");
		if (projectId.isBlank()) projectId = UUID.randomUUID().toString();

		String projectPath = getString(root, "projectPath", abs.toString());
		String timelineName = getString(root, "timelineName", "");
		String audioPath = getString(root, "audioPath", "");
		double durationSeconds = getDouble(root, "durationSeconds", 0.0);
		double bpm = getDouble(root, "bpm", 0.0);
		List<TimelineMarker> markers = parseMarkers(root);
		if (layerManager != null && root.has("buildLayers") && root.get("buildLayers").isJsonArray()) {
			JsonArray groupsArr = root.has("buildLayerGroups") && root.get("buildLayerGroups").isJsonArray()
				? root.getAsJsonArray("buildLayerGroups")
				: null;
			BuildLayerPersistence.loadInto(layerManager, root.getAsJsonArray("buildLayers"), groupsArr);
		}
		if (timeline != null) {
			timeline.setName(timelineName);
			timeline.setDurationSeconds(durationSeconds);
			timeline.setMetadata("projectId", projectId);
			timeline.setMetadata("projectPath", projectPath);
			timeline.setMetadata("audioPath", audioPath.isBlank() ? null : audioPath);
			timeline.setMetadata("bpm", bpm > 0 ? bpm : null);
			timeline.clearMarkers();
			markers.forEach(timeline::addMarker);
			JsonArray animationTracks = root.has("animationTracks") && root.get("animationTracks").isJsonArray()
				? root.getAsJsonArray("animationTracks")
				: null;
			TimelineAnimationPersistence.loadInto(timeline, animationTracks);
			if (root.has("choreography")) {
				ChoreographyPlanPersistence.loadInto(timeline, root.get("choreography"));
			}
		}

		return new LoadedProject(projectId, projectPath, timelineName, audioPath, markers);
	}

	private static List<TimelineMarker> parseMarkers(JsonObject root) {
		List<TimelineMarker> markers = new ArrayList<>();
		if (root == null || !root.has("markers") || root.get("markers").isJsonNull()) return markers;
		try {
			JsonArray arr = root.getAsJsonArray("markers");
			for (int i = 0; i < arr.size(); i++) {
				JsonObject obj = arr.get(i).getAsJsonObject();
				String id = getString(obj, "id", "");
				double timeSeconds = obj.has("timeSeconds") ? obj.get("timeSeconds").getAsDouble() : 0;
				String name = getString(obj, "name", "");
				MarkerType type = MarkerType.fromName(getString(obj, "type", "GENERIC"));
				markers.add(new TimelineMarker(id, timeSeconds, name, type));
			}
		} catch (RuntimeException e) {
			BeatBlock.LOGGER.warn("Failed to parse markers from .osc project, skipping malformed entries", e);
		}
		markers.sort(java.util.Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return markers;
	}

	private static int getInt(JsonObject obj, String key, int def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		try {
			return obj.get(key).getAsInt();
		} catch (RuntimeException e) {
			BeatBlock.LOGGER.debug("Invalid int for key '{}', using default {}", key, def, e);
			return def;
		}
	}

	private static double getDouble(JsonObject obj, String key, double def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		try {
			double value = obj.get(key).getAsDouble();
			return Double.isFinite(value) ? value : def;
		} catch (RuntimeException e) {
			BeatBlock.LOGGER.debug("Invalid double for key '{}', using default {}", key, def, e);
			return def;
		}
	}

	private static String getString(JsonObject obj, String key, String def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		try {
			return obj.get(key).getAsString();
		} catch (RuntimeException e) {
			BeatBlock.LOGGER.debug("Invalid string for key '{}', using default", key, e);
			return def;
		}
	}

	private static String stringMeta(Timeline timeline, String key) {
		Object v = timeline.getMetadata(key);
		if (v == null) return "";
		String s = String.valueOf(v);
		return s.trim();
	}

	public static final class LoadedProject {
		private final String projectId;
		private final String projectPath;
		private final String timelineName;
		private final String audioPath;
		private final List<TimelineMarker> markers;

		public LoadedProject(String projectId, String projectPath, String timelineName, String audioPath, List<TimelineMarker> markers) {
			this.projectId = projectId == null ? "" : projectId;
			this.projectPath = projectPath == null ? "" : projectPath;
			this.timelineName = timelineName == null ? "" : timelineName;
			this.audioPath = audioPath == null ? "" : audioPath;
			this.markers = markers != null ? List.copyOf(markers) : List.of();
		}

		public String getProjectId() { return projectId; }
		public String getProjectPath() { return projectPath; }
		public String getTimelineName() { return timelineName; }
		public String getAudioPath() { return audioPath; }
		public List<TimelineMarker> getMarkers() { return markers; }
	}
}
