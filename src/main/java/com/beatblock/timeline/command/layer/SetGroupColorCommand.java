package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandMergePolicy;
import com.beatblock.timeline.command.MergeableCommand;
import org.jspecify.annotations.NonNull;

/** 设置图层组颜色标记。 */
public final class SetGroupColorCommand implements MergeableCommand {

	private final BuildLayerManager manager;
	private final String groupId;
	private final int newColorArgb;
	private final long mergeAnchorMs;
	private int previousColorArgb;
	private boolean previousColorCaptured;

	public SetGroupColorCommand(BuildLayerManager manager, String groupId, int newColorArgb) {
		this(manager, groupId, newColorArgb, System.currentTimeMillis());
	}

	private SetGroupColorCommand(
		BuildLayerManager manager, String groupId, int newColorArgb, long mergeAnchorMs
	) {
		this.manager = manager;
		this.groupId = groupId;
		this.newColorArgb = newColorArgb;
		this.mergeAnchorMs = mergeAnchorMs;
	}

	@Override
	public long mergeWindowMs() {
		return CommandMergePolicy.DEFAULT_MERGE_WINDOW_MS;
	}

	@Override
	public boolean canMergeWith(@NonNull Command other) {
		return other instanceof SetGroupColorCommand command
			&& manager == command.manager
			&& java.util.Objects.equals(groupId, command.groupId)
			&& CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())
			&& CommandMergePolicy.withinMergeWindow(command.mergeAnchorMs, command.mergeWindowMs());
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		SetGroupColorCommand command = (SetGroupColorCommand) other;
		SetGroupColorCommand merged = new SetGroupColorCommand(
			manager, groupId, command.newColorArgb, mergeAnchorMs);
		merged.previousColorArgb = previousColorArgb;
		merged.previousColorCaptured = previousColorCaptured;
		return merged;
	}

	@Override
	public void execute() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null) {
			return;
		}
		if (!previousColorCaptured) {
			previousColorArgb = group.getColorArgb();
			previousColorCaptured = true;
		}
		group.setColorArgb(newColorArgb);
	}

	@Override
	public void undo() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null) {
			return;
		}
		group.setColorArgb(previousColorArgb);
	}
}
