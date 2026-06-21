package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 从当前选区创建图层（初始 FREE_VISIBLE）。 */
public final class CreateLayerCommand implements com.beatblock.timeline.command.Command {

	private final BuildLayerManager manager;
	private final String name;
	private final List<BlockPos> blocks;
	private BuildLayer created;

	public CreateLayerCommand(BuildLayerManager manager, String name, List<BlockPos> blocks) {
		this.manager = manager;
		this.name = name;
		this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
	}

	public BuildLayer getCreatedLayer() {
		return created;
	}

	@Override
	public void execute() {
		if (manager == null || blocks.isEmpty()) return;
		created = manager.createFromSelection(name, new ArrayList<>(blocks));
	}

	@Override
	public void undo() {
		if (created == null || manager == null) return;
		World world = BuildLayerManager.currentWorld();
		manager.deleteLayer(created, world);
		created = null;
	}
}
