package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandMergePolicy;
import com.beatblock.timeline.command.MergeableCommand;
import org.jspecify.annotations.NonNull;

/** 设置图层颜色标记。 */
public final class SetLayerColorCommand implements MergeableCommand {

	private final BuildLayerManager manager;
	private final String layerId;
	private final int newColorArgb;
	private final long mergeAnchorMs;
	private int previousColorArgb;
	private boolean previousColorCaptured;

	public SetLayerColorCommand(BuildLayerManager manager, String layerId, int newColorArgb) {
		this(manager, layerId, newColorArgb, System.currentTimeMillis());
	}

	private SetLayerColorCommand(
		BuildLayerManager manager, String layerId, int newColorArgb, long mergeAnchorMs
	) {
		this.manager = manager;
		this.layerId = layerId;
		this.newColorArgb = newColorArgb;
		this.mergeAnchorMs = mergeAnchorMs;
	}

	@Override
	public long mergeWindowMs() {
		return CommandMergePolicy.DEFAULT_MERGE_WINDOW_MS;
	}

	@Override
	public boolean canMergeWith(@NonNull Command other) {
		return other instanceof SetLayerColorCommand command
			&& manager == command.manager
			&& java.util.Objects.equals(layerId, command.layerId)
			&& CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())
			&& CommandMergePolicy.withinMergeWindow(command.mergeAnchorMs, command.mergeWindowMs());
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		SetLayerColorCommand command = (SetLayerColorCommand) other;
		SetLayerColorCommand merged = new SetLayerColorCommand(
			manager, layerId, command.newColorArgb, mergeAnchorMs);
		merged.previousColorArgb = previousColorArgb;
		merged.previousColorCaptured = previousColorCaptured;
		return merged;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null) {
			return;
		}
		if (!previousColorCaptured) {
			previousColorArgb = layer.getColorArgb();
			previousColorCaptured = true;
		}
		layer.setColorArgb(newColorArgb);
	}

	@Override
	public void undo() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null) {
			return;
		}
		layer.setColorArgb(previousColorArgb);
	}
}
