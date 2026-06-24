package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ConnectedSelectionTool {

	private ConnectedSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		host.mergeFromConnected(world, pos, host.resolveOperation(shiftDown));
	}
}
