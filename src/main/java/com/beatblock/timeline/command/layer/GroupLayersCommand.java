package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将多个图层归入新组。 */
public final class GroupLayersCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String groupName;
	private final List<String> layerIds;
	private @Nullable BuildLayerGroup createdGroup;
	private final List<String> previousGroupIds = new ArrayList<>();
	private final Map<String, BuildLayerGroup> previousGroups = new LinkedHashMap<>();

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
		previousGroups.clear();
		for (String layerId : layerIds) {
			BuildLayer layer = manager.get(layerId);
			String previousGroupId = layer != null ? layer.getGroupId() : null;
			previousGroupIds.add(previousGroupId);
			BuildLayerGroup previousGroup = manager.getGroup(previousGroupId);
			if (previousGroup != null) {
				previousGroups.putIfAbsent(previousGroupId, new BuildLayerGroup(
					previousGroup.getId(), previousGroup.getName(), previousGroup.getColorArgb()));
			}
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
		for (BuildLayerGroup previousGroup : previousGroups.values()) {
			manager.registerGroup(previousGroup);
		}
		for (int i = 0; i < layerIds.size(); i++) {
			BuildLayer layer = manager.get(layerIds.get(i));
			if (layer == null) {
				continue;
			}
			String previous = i < previousGroupIds.size() ? previousGroupIds.get(i) : null;
			layer.setGroupId(previous);
		}
		createdGroup = null;
	}
}
