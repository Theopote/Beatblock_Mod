package com.beatblock.timeline.project;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Transactional project document open: read → scratch validate → backup live → commit.
 * Open failure before/during commit restores the previous live document when possible.
 */
public final class ProjectDocumentLoader {

	private ProjectDocumentLoader() {}

	public record OpenDocumentResult(
		OscProjectStore.LoadedProject loaded,
		boolean committed
	) {}

	/**
	 * Load {@code filePath} into the live Timeline / layers only after a successful scratch apply.
	 * If live apply fails, restores from an in-memory backup of the prior live document.
	 */
	public static OpenDocumentResult openTransactional(
		Path filePath,
		Timeline liveTimeline,
		@Nullable BuildLayerManager liveLayers
	) throws IOException {
		if (liveTimeline == null) {
			throw new IOException("打开失败：Timeline 为空");
		}
		JsonObject candidate = OscProjectStore.readRoot(filePath);
		Path abs = filePath.toAbsolutePath().normalize();
		if (isBlank(candidate, "projectPath")) {
			candidate.addProperty("projectPath", abs.toString());
		}

		// Scratch dry-run: prove apply works without touching live.
		Timeline scratchTimeline = Timeline.createDefault();
		BuildLayerManager scratchLayers = new BuildLayerManager(new StageObjectSystem());
		OscProjectStore.applyRoot(OscProjectStore.copyRoot(candidate), scratchLayers, scratchTimeline);

		JsonObject liveBackup = OscProjectStore.toRoot(
			liveTimeline,
			liveLayers,
			stringOrEmpty(liveTimeline.getMetadata("projectPath")),
			stringOrEmpty(liveTimeline.getMetadata("projectId"))
		);

		try {
			OscProjectStore.LoadedProject loaded = OscProjectStore.applyRoot(
				OscProjectStore.copyRoot(candidate), liveLayers, liveTimeline);
			return new OpenDocumentResult(loaded, true);
		} catch (RuntimeException | IOException commitFailure) {
			try {
				OscProjectStore.applyRoot(OscProjectStore.copyRoot(liveBackup), liveLayers, liveTimeline);
			} catch (RuntimeException | IOException restoreFailure) {
				commitFailure.addSuppressed(restoreFailure);
			}
			if (commitFailure instanceof IOException io) {
				throw io;
			}
			throw new IOException("打开失败：写入当前工程时出错: " + commitFailure.getMessage(), commitFailure);
		}
	}

	private static boolean isBlank(JsonObject root, String key) {
		if (root == null || !root.has(key) || root.get(key).isJsonNull()) return true;
		try {
			return root.get(key).getAsString().isBlank();
		} catch (RuntimeException ex) {
			return true;
		}
	}

	private static String stringOrEmpty(@Nullable Object value) {
		return value != null ? String.valueOf(value).trim() : "";
	}
}
