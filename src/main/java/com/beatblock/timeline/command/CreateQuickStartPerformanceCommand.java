package com.beatblock.timeline.command;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.presenter.QuickStartTimelineSnapshot;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Start「一键生成首次表演」复合命令。
 * <p>
 * 内部按语义拆分（便于 Undo 历史与未来拆成真正独立 generator）：
 * <ol>
 *   <li>{@link CreateStageObjectCommand}</li>
 *   <li>Generate Choreography — animation tracks + plan metadata</li>
 *   <li>Generate Camera — optional</li>
 *   <li>Generate VFX — optional</li>
 * </ol>
 * 当前 AutoMap 仍可能一次写出多轨；子命令用 before/after 快照切片保证原子 Undo/Redo。
 */
public final class CreateQuickStartPerformanceCommand implements Command {

	private static final String[] CHOREOGRAPHY_TRACKS = {
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_ANIMATION_BLOCK
	};
	private static final String[] CAMERA_TRACKS = {
		Timeline.TRACK_ID_CAMERA
	};
	private static final String[] VFX_TRACKS = {
		Timeline.TRACK_ID_GLOBAL
	};

	private final CompositeCommand composite;
	private final @Nullable String createdObjectId;
	private final boolean includeCamera;
	private final boolean includeVfx;

	private CreateQuickStartPerformanceCommand(
		CompositeCommand composite,
		@Nullable String createdObjectId,
		boolean includeCamera,
		boolean includeVfx
	) {
		this.composite = composite != null ? composite : new CompositeCommand(List.of());
		this.createdObjectId = createdObjectId;
		this.includeCamera = includeCamera;
		this.includeVfx = includeVfx;
	}

	/**
	 * 生成已落地后构造：压入 {@link CommandManager} 时首次 execute 为 no-op。
	 * {@code includeCamera}/{@code includeVfx} 保留给调用方语义；为确保 Undo 完整，
	 * Camera / VFX 轨道切片始终纳入复合命令（无写入时 before==after，为 no-op 切片）。
	 */
	public static CreateQuickStartPerformanceCommand alreadyApplied(
		@Nullable Timeline timeline,
		@Nullable StageObjectSystem stageObjects,
		QuickStartTimelineSnapshot before,
		QuickStartTimelineSnapshot after,
		@Nullable RuntimeStageObject createdObject,
		boolean includeCamera,
		boolean includeVfx
	) {
		CreateStageObjectCommand stageCmd = CreateStageObjectCommand.alreadyApplied(stageObjects, createdObject);
		List<Command> parts = new ArrayList<>(4);
		parts.add(stageCmd);
		parts.add(ApplyTimelineTracksCommand.alreadyApplied(
			timeline, before, after, CHOREOGRAPHY_TRACKS, true
		));
		// Always slice camera/vfx tracks so undo never leaves AutoMap leftovers.
		parts.add(ApplyTimelineTracksCommand.alreadyApplied(
			timeline, before, after, CAMERA_TRACKS, false
		));
		parts.add(ApplyTimelineTracksCommand.alreadyApplied(
			timeline, before, after, VFX_TRACKS, false
		));
		return new CreateQuickStartPerformanceCommand(
			new CompositeCommand(parts),
			stageCmd.createdObjectId(),
			includeCamera,
			includeVfx
		);
	}

	public int commandCount() {
		return composite.commandCount();
	}

	public @Nullable String createdObjectId() {
		return createdObjectId;
	}

	public boolean includesCamera() {
		return includeCamera;
	}

	public boolean includesVfx() {
		return includeVfx;
	}

	@Override
	public void execute() {
		composite.execute();
	}

	@Override
	public void undo() {
		composite.undo();
	}
}
