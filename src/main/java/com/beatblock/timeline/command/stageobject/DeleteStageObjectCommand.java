package com.beatblock.timeline.command.stageobject;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.StageObjectReferenceService;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * 删除独立 StageObject；可选在删除前清除 Timeline / AutoMap 等引用。
 */
public final class DeleteStageObjectCommand implements com.beatblock.timeline.command.Command {

	private final StageObjectSystem system;
	private final @Nullable Timeline timeline;
	private final String stageObjectId;
	private final boolean clearReferences;
	private @Nullable RuntimeStageObject snapshot;
	private StageObjectReferenceService.@Nullable MutationResult clearedReferences;

	public DeleteStageObjectCommand(StageObjectSystem system, String stageObjectId) {
		this(system, null, stageObjectId, false);
	}

	public DeleteStageObjectCommand(
		StageObjectSystem system,
		@Nullable Timeline timeline,
		String stageObjectId,
		boolean clearReferences
	) {
		this.system = system;
		this.timeline = timeline;
		this.stageObjectId = stageObjectId;
		this.clearReferences = clearReferences;
	}

	@Override
	public void execute() {
		if (system == null || stageObjectId == null || stageObjectId.isBlank()) {
			return;
		}
		snapshot = system.get(stageObjectId);
		if (snapshot == null) {
			return;
		}
		clearedReferences = null;
		if (clearReferences && timeline != null) {
			clearedReferences = StageObjectReferenceService.clear(timeline, Set.of(stageObjectId));
		}
		system.remove(stageObjectId);
	}

	@Override
	public void undo() {
		if (snapshot == null || system == null) {
			return;
		}
		system.register(snapshot);
		if (timeline != null && clearedReferences != null) {
			StageObjectReferenceService.restore(timeline, clearedReferences);
			clearedReferences = null;
		}
	}
}
