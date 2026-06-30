package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 将多个图层归入新组。 */
public final class GroupLayersCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String groupName;
	private final List<String> layerIds;
	private @Nullable BuildLayerGroup createdGroup;
	private final List<String> previousGroupIds = new ArrayList<>();

	public GroupLayersCommand(BuildLayerManager manager, String groupName, List<String> layerIds) {
		this.manager = manager;
		this.groupName = groupName;
		this.layerIds = layerIds != null ? List.copyOf(layerIds) : List.of();
	}

	public @Nullable BuildLayerGroup getCreatedGroup() {
		return createdGroup;
	}

	@Override
	public void execute() {
		if (manager == null || layerIds.isEmpty()) {
			return;
		}
		previousGroupIds.clear();
		for (String layerId : layerIds) {
			BuildLayer layer = manager.get(layerId);
			previousGroupIds.add(layer != null ? layer.getGroupId() : null);
		}
		createdGroup = manager.createGroup(groupName, layerIds);
	}

	@Override
	public void undo() {
		if (manager == null || createdGroup == null) {
			return;
		}
		String groupId = createdGroup.getId();
		manager.dissolveGroup(groupId);
		for (int i = 0; i < layerIds.size(); i++) {
			BuildLayer layer = manager.get(layerIds.get(i));
			if (layer == null) {
				continue;
			}
			String previous = i < previousGroupIds.size() ? previousGroupIds.get(i) : null;
			layer.setGroupId(previous);
			if (previous != null && manager.getGroup(previous) == null) {
				manager.registerGroup(new BuildLayerGroup(previous, previous, 0));
			}
		}
		createdGroup = null;
	}
}
