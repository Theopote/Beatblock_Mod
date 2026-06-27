package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;

import org.jspecify.annotations.Nullable;

/** 重命名图层（名称不可与其他图层重复）。 */
public final class RenameLayerCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String layerId;
	private final String newName;
	private @Nullable String previousName;

	public RenameLayerCommand(BuildLayerManager manager, String layerId, String newName) {
		this.manager = manager;
		this.layerId = layerId;
		this.newName = newName;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || newName == null || newName.isBlank()) return;
		previousName = layer.getName();
		manager.renameLayer(layer, newName.trim());
	}

	@Override
	public void undo() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || previousName == null) return;
		manager.renameLayer(layer, previousName);
	}
}
