package com.beatblock.timeline.project;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Transactional project document load / new-project reset.
 * Failures restore the previous live document when a backup was taken.
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

		JsonObject liveBackup = snapshotLive(liveTimeline, liveLayers);

		try {
			OscProjectStore.LoadedProject loaded = OscProjectStore.applyRoot(
				OscProjectStore.copyRoot(candidate), liveLayers, liveTimeline);
			return new OpenDocumentResult(loaded, true);
		} catch (RuntimeException | IOException commitFailure) {
			restoreLive(liveBackup, liveTimeline, liveLayers, commitFailure);
			if (commitFailure instanceof IOException io) {
				throw io;
			}
			throw new IOException("打开失败：写入当前工程时出错: " + commitFailure.getMessage(), commitFailure);
		}
	}

	/**
	 * Test-only: runs after {@link Timeline#resetToEmptyProject()} and before layer purge,
	 * so tests can force a mid-reset failure and assert backup restore.
	 */
	static @Nullable volatile Runnable newProjectFailureInjectorForTests;

	/**
	 * Reset live document to a new empty project. On failure, restores from an in-memory backup
	 * so New Project never leaves a half-destroyed session.
	 *
	 * @param newProjectId non-blank id written into metadata on success
	 */
	public static void newProjectTransactional(
		Timeline liveTimeline,
		@Nullable BuildLayerManager liveLayers,
		String newProjectId
	) throws IOException {
		if (liveTimeline == null) {
			throw new IOException("新建失败：Timeline 为空");
		}
		String id = newProjectId != null && !newProjectId.isBlank()
			? newProjectId.trim()
			: java.util.UUID.randomUUID().toString();
		JsonObject liveBackup = snapshotLive(liveTimeline, liveLayers);
		try {
			liveTimeline.resetToEmptyProject();
			Runnable injector = newProjectFailureInjectorForTests;
			if (injector != null) {
				injector.run();
			}
			if (liveLayers != null) {
				liveLayers.purgeAllLayers();
			}
			liveTimeline.setMetadata("projectId", id);
			liveTimeline.setMetadata("projectPath", null);
			liveTimeline.setMetadata("audioPath", null);
		} catch (RuntimeException resetFailure) {
			restoreLive(liveBackup, liveTimeline, liveLayers, resetFailure);
			throw new IOException("新建失败：重置当前工程时出错: " + resetFailure.getMessage(), resetFailure);
		}
	}

	private static JsonObject snapshotLive(Timeline liveTimeline, @Nullable BuildLayerManager liveLayers) {
		return OscProjectStore.toRoot(
			liveTimeline,
			liveLayers,
			stringOrEmpty(liveTimeline.getMetadata("projectPath")),
			stringOrEmpty(liveTimeline.getMetadata("projectId"))
		);
	}

	private static void restoreLive(
		JsonObject liveBackup,
		Timeline liveTimeline,
		@Nullable BuildLayerManager liveLayers,
		Throwable primary
	) {
		try {
			OscProjectStore.applyRoot(OscProjectStore.copyRoot(liveBackup), liveLayers, liveTimeline);
		} catch (RuntimeException | IOException restoreFailure) {
			primary.addSuppressed(restoreFailure);
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
