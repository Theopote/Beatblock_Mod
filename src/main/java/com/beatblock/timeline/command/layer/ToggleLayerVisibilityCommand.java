package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import net.minecraft.world.World;

/** 切换图层 FREE_VISIBLE ↔ FREE_HIDDEN。 */
public final class ToggleLayerVisibilityCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String layerId;
	private LayerVisibilityState previousState;

	public ToggleLayerVisibilityCommand(BuildLayerManager manager, String layerId) {
		this.manager = manager;
		this.layerId = layerId;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || !layer.canToggleVisibility()) return;
		previousState = layer.getState();
		World world = BuildLayerManager.currentWorld();
		if (world == null) return;
		if (layer.getState() == LayerVisibilityState.FREE_VISIBLE) {
			manager.hideLayer(layer, world);
		} else if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) {
			manager.showLayer(layer, world);
		}
	}

	@Override
	public void undo() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null || previousState == null) return;
		World world = BuildLayerManager.currentWorld();
		if (world == null) return;
		if (previousState == LayerVisibilityState.FREE_VISIBLE) {
			manager.showLayer(layer, world);
		} else if (previousState == LayerVisibilityState.FREE_HIDDEN) {
			manager.hideLayer(layer, world);
		}
	}
}
