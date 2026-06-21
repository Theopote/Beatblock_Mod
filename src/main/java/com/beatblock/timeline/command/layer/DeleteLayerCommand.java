package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import net.minecraft.world.World;

/** 删除图层（FREE 状态下会先恢复方块）。 */
public final class DeleteLayerCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String layerId;
	private BuildLayer snapshot;

	public DeleteLayerCommand(BuildLayerManager manager, String layerId) {
		this.manager = manager;
		this.layerId = layerId;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || !layer.canDelete()) return;
		snapshot = layer;
		World world = BuildLayerManager.currentWorld();
		manager.deleteLayer(layer, world);
	}

	@Override
	public void undo() {
		if (snapshot == null || manager == null) return;
		manager.registerRestored(snapshot);
	}
}
