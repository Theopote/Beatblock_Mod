package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class BoxSelectionTool {

	private BoxSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		BlockPos immutable = pos.toImmutable();
		if (host.getBoxFirstCorner() == null) {
			host.setBoxFirstCorner(immutable);
			host.setMessage("框选：已设角点 A，再点角点 B");
			return;
		}
		BlockPos a = host.getBoxFirstCorner();
		host.clearBoxFirstCorner();
		host.mergeFromBox(world, a, immutable, host.getDefaultOperation());
	}
}
