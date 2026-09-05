package com.beatblock.timeline.command;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.presenter.QuickStartTimelineSnapshot;

import org.jspecify.annotations.Nullable;

/**
 * @deprecated 使用 {@link CreateQuickStartPerformanceCommand}。
 */
@Deprecated
public final class QuickStartGenerateCommand implements Command {

	private final CreateQuickStartPerformanceCommand delegate;

	private QuickStartGenerateCommand(CreateQuickStartPerformanceCommand delegate) {
		this.delegate = delegate;
	}

	public static QuickStartGenerateCommand alreadyApplied(
		@Nullable Timeline timeline,
		@Nullable StageObjectSystem stageObjects,
		QuickStartTimelineSnapshot before,
		QuickStartTimelineSnapshot after,
		@Nullable RuntimeStageObject createdObject
	) {
		return new QuickStartGenerateCommand(CreateQuickStartPerformanceCommand.alreadyApplied(
			timeline, stageObjects, before, after, createdObject, true, true
		));
	}

	public @Nullable String createdObjectId() {
		return delegate.createdObjectId();
	}

	public boolean isApplied() {
		return true;
	}

	@Override
	public void execute() {
		delegate.execute();
	}

	@Override
	public void undo() {
		delegate.undo();
	}
}
