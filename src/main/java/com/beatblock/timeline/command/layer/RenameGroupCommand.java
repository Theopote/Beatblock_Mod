package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;

/** 重命名图层组。 */
public final class RenameGroupCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String groupId;
	private final String newName;
	private String previousName;

	public RenameGroupCommand(BuildLayerManager manager, String groupId, String newName) {
		this.manager = manager;
		this.groupId = groupId;
		this.newName = newName;
	}

	@Override
	public void execute() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null) {
			return;
		}
		previousName = group.getName();
		manager.renameGroup(group, newName);
	}

	@Override
	public void undo() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null || previousName == null) {
			return;
		}
		group.setName(previousName);
	}
}
