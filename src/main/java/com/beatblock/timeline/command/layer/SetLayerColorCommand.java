package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;

/** 设置图层颜色标记。 */
public final class SetLayerColorCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String layerId;
	private final int newColorArgb;
	private int previousColorArgb;

	public SetLayerColorCommand(BuildLayerManager manager, String layerId, int newColorArgb) {
		this.manager = manager;
		this.layerId = layerId;
		this.newColorArgb = newColorArgb;
	}

	@Override
	public void execute() {
		BuildLayer layer = manager != null ? manager.get(layerId) : null;
		if (layer == null) {
			return;
		}
		previousColorArgb = layer.getColorArgb();
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
