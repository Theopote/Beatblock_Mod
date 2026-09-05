package com.beatblock.timeline.command;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;

import org.jspecify.annotations.Nullable;

/**
 * Quick Start 子命令：创建 / 删除 StageObject。
 */
public final class CreateStageObjectCommand implements Command {

	private final @Nullable StageObjectSystem stageObjects;
	private final @Nullable RuntimeStageObject createdObject;
	private boolean applied;

	private CreateStageObjectCommand(
		@Nullable StageObjectSystem stageObjects,
		@Nullable RuntimeStageObject createdObject,
		boolean applied
	) {
		this.stageObjects = stageObjects;
		this.createdObject = createdObject;
		this.applied = applied;
	}

	public static CreateStageObjectCommand alreadyApplied(
		@Nullable StageObjectSystem stageObjects,
		@Nullable RuntimeStageObject createdObject
	) {
		return new CreateStageObjectCommand(stageObjects, createdObject, true);
	}

	public @Nullable String createdObjectId() {
		return createdObject != null ? createdObject.getId() : null;
	}

	@Override
	public void execute() {
		if (applied) {
			return;
		}
		if (stageObjects != null && createdObject != null) {
			stageObjects.register(createdObject);
		}
		applied = true;
	}

	@Override
	public void undo() {
		if (!applied) {
			return;
		}
		if (stageObjects != null && createdObject != null) {
			stageObjects.remove(createdObject.getId());
		}
		applied = false;
	}
}
