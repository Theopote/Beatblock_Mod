package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;

/**
 * 片段拖动 / 缩放：execute 应用 after 快照，undo 恢复 before 快照。
 */
public final class ApplyClipDragCommand implements MergeableCommand {

	private final Timeline timeline;
	private final ClipDragStateSnapshot before;
	private final ClipDragStateSnapshot after;
	private final long mergeAnchorMs;

	public ApplyClipDragCommand(Timeline timeline, ClipDragStateSnapshot before, ClipDragStateSnapshot after) {
		this(timeline, before, after, System.currentTimeMillis());
	}

	ApplyClipDragCommand(
		Timeline timeline,
		ClipDragStateSnapshot before,
		ClipDragStateSnapshot after,
		long mergeAnchorMs
	) {
		this.timeline = timeline;
		this.before = before;
		this.after = after;
		this.mergeAnchorMs = mergeAnchorMs;
	}

	@Override
	public long mergeWindowMs() {
		return CommandMergePolicy.DEFAULT_MERGE_WINDOW_MS;
	}

	@Override
	public boolean canMergeWith(Command other) {
		if (!(other instanceof ApplyClipDragCommand cmd)) return false;
		if (!CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())) return false;
		if (!CommandMergePolicy.withinMergeWindow(cmd.mergeAnchorMs, cmd.mergeWindowMs())) return false;
		return timeline == cmd.timeline && after.equals(cmd.before);
	}

	@Override
	public Command mergeWith(Command other) {
		ApplyClipDragCommand cmd = (ApplyClipDragCommand) other;
		return new ApplyClipDragCommand(timeline, before, cmd.after, mergeAnchorMs);
	}

	@Override
	public void execute() {
		if (after != null) after.applyTo(timeline);
	}

	@Override
	public void undo() {
		if (before != null) before.applyTo(timeline);
	}
}
