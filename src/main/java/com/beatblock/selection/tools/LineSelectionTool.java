package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class LineSelectionTool {

	private LineSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		BlockPos immutable = pos.toImmutable();
		if (host.getLineFirstCorner() == null) {
			host.setLineFirstCorner(immutable);
			host.setMessage("线选：已设端点 A，再点端点 B");
			return;
		}
		BlockPos a = host.getLineFirstCorner();
		host.clearLineFirstCorner();
		host.mergeFromLine(world, a, immutable, host.getDefaultOperation());
	}
}
