package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.ui.presenter.QuickStartTimelineSnapshot;

import org.jspecify.annotations.Nullable;

/**
 * Quick Start 子命令：在 before/after 快照之间切换指定 Timeline 轨道。
 */
public final class ApplyTimelineTracksCommand implements Command {

	private final @Nullable Timeline timeline;
	private final QuickStartTimelineSnapshot before;
	private final QuickStartTimelineSnapshot after;
	private final String[] trackIds;
	private final boolean restoreMetadata;
	private boolean applied;

	private ApplyTimelineTracksCommand(
		@Nullable Timeline timeline,
		QuickStartTimelineSnapshot before,
		QuickStartTimelineSnapshot after,
		String[] trackIds,
		boolean restoreMetadata,
		boolean applied
	) {
		this.timeline = timeline;
		this.before = before != null ? before : QuickStartTimelineSnapshot.capture(null);
		this.after = after != null ? after : QuickStartTimelineSnapshot.capture(null);
		this.trackIds = trackIds != null ? trackIds.clone() : new String[0];
		this.restoreMetadata = restoreMetadata;
		this.applied = applied;
	}

	public static ApplyTimelineTracksCommand alreadyApplied(
		@Nullable Timeline timeline,
		QuickStartTimelineSnapshot before,
		QuickStartTimelineSnapshot after,
		String[] trackIds,
		boolean restoreMetadata
	) {
		return new ApplyTimelineTracksCommand(timeline, before, after, trackIds, restoreMetadata, true);
	}

	@Override
	public void execute() {
		if (applied) {
			return;
		}
		after.restoreTracks(timeline, trackIds, restoreMetadata);
		applied = true;
	}

	@Override
	public void undo() {
		if (!applied) {
			return;
		}
		before.restoreTracks(timeline, trackIds, restoreMetadata);
		applied = false;
	}
}
