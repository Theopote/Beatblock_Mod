package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;

/** 设置图层组颜色标记。 */
public final class SetGroupColorCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String groupId;
	private final int newColorArgb;
	private int previousColorArgb;

	public SetGroupColorCommand(BuildLayerManager manager, String groupId, int newColorArgb) {
		this.manager = manager;
		this.groupId = groupId;
		this.newColorArgb = newColorArgb;
	}

	@Override
	public void execute() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null) {
			return;
		}
		previousColorArgb = group.getColorArgb();
		group.setColorArgb(newColorArgb);
	}

	@Override
	public void undo() {
		BuildLayerGroup group = manager != null ? manager.getGroup(groupId) : null;
		if (group == null) {
			return;
		}
		group.setColorArgb(previousColorArgb);
	}
}
