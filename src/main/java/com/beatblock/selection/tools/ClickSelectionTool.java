package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ClickSelectionTool {

	private ClickSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		host.handleDirectClick(world, pos, host.resolveOperation(shiftDown));
	}
}
