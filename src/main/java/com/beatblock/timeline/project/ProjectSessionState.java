package com.beatblock.timeline.project;

import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Project-level session identity and dirty tracking.
 * <p>
 * Dirty is {@code documentGeneration != savedGeneration}, not Undo stack depth.
 * Mutations bump generation via {@link com.beatblock.timeline.editing.TimelineDocumentChangeNotifier}.
 */
public final class ProjectSessionState {

	private static final ProjectSessionState INSTANCE = new ProjectSessionState();

	private String projectId = "";
	private @Nullable Path projectPath;
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
		projectPath = null;
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

	public synchronized @Nullable Path projectPath() {
		return projectPath;
	}

	/** String form for UI / metadata; empty when unset. */
	public synchronized String projectPathString() {
		return projectPath != null ? projectPath.toString() : "";
	}

	/** Called from {@link com.beatblock.timeline.editing.TimelineDocumentChangeNotifier}. */
	public synchronized void markDocumentEdited() {
		documentGeneration++;
	}

	public synchronized void markClean() {
		savedGeneration = documentGeneration;
	}

	public synchronized void markSaved(@Nullable Path path) {
		if (path != null) {
			this.projectPath = path.toAbsolutePath().normalize();
		}
		markClean();
	}

	public synchronized void markOpened(@Nullable Path path) {
		if (path != null) {
			this.projectPath = path.toAbsolutePath().normalize();
		}
		markClean();
	}

	public synchronized void markNewProject() {
		this.projectId = java.util.UUID.randomUUID().toString();
		this.projectPath = null;
		markClean();
	}

	public synchronized void markNewProject(String newProjectId) {
		this.projectId = newProjectId != null && !newProjectId.isBlank()
			? newProjectId.trim()
			: java.util.UUID.randomUUID().toString();
		this.projectPath = null;
		markClean();
	}

	public synchronized void syncIdentity(@Nullable String id, @Nullable String path) {
		this.projectId = id != null ? id.trim() : "";
		if (path == null || path.isBlank()) {
			this.projectPath = null;
		} else {
			try {
				this.projectPath = Path.of(path.trim()).toAbsolutePath().normalize();
			} catch (RuntimeException ex) {
				this.projectPath = null;
			}
		}
	}

	public synchronized void onProjectSaved(@Nullable String id, @Nullable String path) {
		if (id != null && !id.isBlank()) {
			this.projectId = id.trim();
		}
		markSaved(path != null && !path.isBlank() ? Path.of(path) : null);
	}

	public synchronized void onProjectOpened(@Nullable String id, @Nullable String path) {
		if (id != null && !id.isBlank()) {
			this.projectId = id.trim();
		}
		markOpened(path != null && !path.isBlank() ? Path.of(path) : null);
	}

	public synchronized void onNewProject(@Nullable String newProjectId) {
		markNewProject(newProjectId != null ? newProjectId : "");
	}

	/** Align session mirrors with live Timeline metadata (app start / bind). */
	public synchronized void bindFromTimeline(@Nullable Timeline timeline) {
		if (timeline == null) {
			projectId = "";
			projectPath = null;
			return;
		}
		Object id = timeline.getMetadata("projectId");
		Object path = timeline.getMetadata("projectPath");
		projectId = id != null ? String.valueOf(id).trim() : "";
		String pathStr = path != null ? String.valueOf(path).trim() : "";
		if (pathStr.isBlank()) {
			projectPath = null;
		} else {
			try {
				projectPath = Path.of(pathStr).toAbsolutePath().normalize();
			} catch (RuntimeException ex) {
				projectPath = null;
			}
		}
	}
}
