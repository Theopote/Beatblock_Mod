package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayerManager;

import java.util.ArrayList;
import java.util.List;

/** 调整图层面板中的图层显示顺序。 */
public final class ReorderLayerCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String movingLayerId;
	private final String targetLayerId;
	private List<String> previousOrder = List.of();

	public ReorderLayerCommand(BuildLayerManager manager, String movingLayerId, String targetLayerId) {
		this.manager = manager;
		this.movingLayerId = movingLayerId;
		this.targetLayerId = targetLayerId;
	}

	@Override
	public void execute() {
		if (manager == null) {
			return;
		}
		previousOrder = new ArrayList<>(manager.getLayerOrderIds());
		manager.moveLayerBefore(movingLayerId, targetLayerId);
	}

	@Override
	public void undo() {
		if (manager == null || previousOrder.isEmpty()) {
			return;
		}
		manager.setLayerOrder(previousOrder);
	}
}
