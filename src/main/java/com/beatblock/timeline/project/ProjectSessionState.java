package com.beatblock.timeline.project;

import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

/**
 * Project-level session identity and dirty tracking.
 * <p>
 * Dirty is {@code documentGeneration != savedGeneration}, not Undo stack depth.
 * Mutations bump generation via {@link com.beatblock.timeline.editing.TimelineDocumentChangeNotifier}.
 */
public final class ProjectSessionState {

	private static final ProjectSessionState INSTANCE = new ProjectSessionState();

	private String projectId = "";
	private String projectPath = "";
	private long documentGeneration;
	private long savedGeneration;

	private ProjectSessionState() {}

	public static ProjectSessionState get() {
		return INSTANCE;
	}

	/** Test-only: reset generations and identity. */
	public static void resetForTests() {
		INSTANCE.resetInternal();
	}

	private synchronized void resetInternal() {
		projectId = "";
		projectPath = "";
		documentGeneration = 0;
		savedGeneration = 0;
	}

	public synchronized boolean isDirty() {
		return documentGeneration != savedGeneration;
	}

	public synchronized long documentGeneration() {
		return documentGeneration;
	}

	public synchronized long savedGeneration() {
		return savedGeneration;
	}

	public synchronized String projectId() {
		return projectId;
	}

	public synchronized String projectPath() {
		return projectPath;
	}

	/** Called from {@link com.beatblock.timeline.editing.TimelineDocumentChangeNotifier}. */
	public synchronized void markDocumentEdited() {
		documentGeneration++;
	}

	public synchronized void markClean() {
		savedGeneration = documentGeneration;
	}

	public synchronized void syncIdentity(@Nullable String id, @Nullable String path) {
		this.projectId = id != null ? id.trim() : "";
		this.projectPath = path != null ? path.trim() : "";
	}

	public synchronized void onProjectSaved(@Nullable String id, @Nullable String path) {
		syncIdentity(id, path);
		markClean();
	}

	public synchronized void onProjectOpened(@Nullable String id, @Nullable String path) {
		syncIdentity(id, path);
		markClean();
	}

	public synchronized void onNewProject(@Nullable String newProjectId) {
		this.projectId = newProjectId != null && !newProjectId.isBlank()
			? newProjectId.trim()
			: java.util.UUID.randomUUID().toString();
		this.projectPath = "";
		markClean();
	}

	/** Align session mirrors with live Timeline metadata (app start / bind). */
	public synchronized void bindFromTimeline(@Nullable Timeline timeline) {
		if (timeline == null) {
			projectId = "";
			projectPath = "";
			return;
		}
		Object id = timeline.getMetadata("projectId");
		Object path = timeline.getMetadata("projectPath");
		projectId = id != null ? String.valueOf(id).trim() : "";
		projectPath = path != null ? String.valueOf(path).trim() : "";
	}
}
