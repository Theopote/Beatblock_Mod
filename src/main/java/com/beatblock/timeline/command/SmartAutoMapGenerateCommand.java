package com.beatblock.timeline.command;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.generation.GenerationDocumentSnapshot;

import org.jspecify.annotations.Nullable;

/**
 * One Smart AutoMap generate = one Undo/Redo step via before/after document snapshots.
 */
public final class SmartAutoMapGenerateCommand implements AppliedCommand {

	private final @Nullable Timeline timeline;
	private final @Nullable BuildLayerManager layerManager;
	private final GenerationDocumentSnapshot before;
	private final GenerationDocumentSnapshot after;
	private final SmartAutoMapEngine.AutoMapResult result;
	private boolean applied;

	private SmartAutoMapGenerateCommand(
		@Nullable Timeline timeline,
		@Nullable BuildLayerManager layerManager,
		GenerationDocumentSnapshot before,
		GenerationDocumentSnapshot after,
		SmartAutoMapEngine.AutoMapResult result,
		boolean applied
	) {
		this.timeline = timeline;
		this.layerManager = layerManager;
		this.before = before != null
			? before
			: GenerationDocumentSnapshot.capture(null, null);
		this.after = after != null
			? after
			: GenerationDocumentSnapshot.capture(null, null);
		this.result = result != null ? result : new SmartAutoMapEngine.AutoMapResult(0, 0, 0, 0, "");
		this.applied = applied;
	}

	/**
	 * Capture before, run generate into {@code timeline}, capture after.
	 * Returned command is already applied — first {@link CommandManager#execute} is a no-op push.
	 */
	public static SmartAutoMapGenerateCommand runAndCapture(
		@Nullable AudioFeatureTimeline featureTimeline,
		@Nullable AutoMapSettings settings,
		@Nullable Timeline timeline,
		@Nullable BuildLayerManager layerManager
	) {
		GenerationDocumentSnapshot before = GenerationDocumentSnapshot.capture(timeline, layerManager);
		SmartAutoMapEngine.AutoMapResult result =
			SmartAutoMapEngine.generate(featureTimeline, settings, timeline);
		GenerationDocumentSnapshot after = GenerationDocumentSnapshot.capture(timeline, layerManager);
		return new SmartAutoMapGenerateCommand(timeline, layerManager, before, after, result, true);
	}

	public SmartAutoMapEngine.AutoMapResult result() {
		return result;
	}

	@Override
	public boolean wasApplied() {
		return applied;
	}

	@Override
	public void execute() {
		if (applied) {
			return;
		}
		after.restore(timeline, layerManager);
		applied = true;
	}

	@Override
	public void undo() {
		if (!applied) {
			return;
		}
		before.restore(timeline, layerManager);
		applied = false;
	}
}
