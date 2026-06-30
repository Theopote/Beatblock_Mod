package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;

import java.util.ArrayList;
import java.util.List;

/** 将图层从组中移出。 */
public final class UngroupLayersCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final List<String> layerIds;
	private final List<String> previousGroupIds = new ArrayList<>();
	private final List<BuildLayerGroup> dissolvedGroups = new ArrayList<>();

	public UngroupLayersCommand(BuildLayerManager manager, List<String> layerIds) {
		this.manager = manager;
		this.layerIds = layerIds != null ? List.copyOf(layerIds) : List.of();
	}

	@Override
	public void execute() {
		if (manager == null) {
			return;
		}
		previousGroupIds.clear();
		dissolvedGroups.clear();
		for (String layerId : layerIds) {
			BuildLayer layer = manager.get(layerId);
			if (layer == null) {
				previousGroupIds.add(null);
				continue;
			}
			String groupId = layer.getGroupId();
			previousGroupIds.add(groupId);
			if (groupId != null) {
				BuildLayerGroup group = manager.getGroup(groupId);
				if (group != null && dissolvedGroups.stream().noneMatch(g -> g.getId().equals(groupId))) {
					dissolvedGroups.add(new BuildLayerGroup(group.getId(), group.getName(), group.getColorArgb()));
				}
			}
		}
		manager.ungroupLayers(layerIds);
	}

	@Override
	public void undo() {
		if (manager == null) {
			return;
		}
		for (BuildLayerGroup group : dissolvedGroups) {
			manager.registerGroup(group);
		}
		for (int i = 0; i < layerIds.size(); i++) {
			BuildLayer layer = manager.get(layerIds.get(i));
			if (layer == null) {
				continue;
			}
			String previous = i < previousGroupIds.size() ? previousGroupIds.get(i) : null;
			layer.setGroupId(previous);
		}
	}
}
