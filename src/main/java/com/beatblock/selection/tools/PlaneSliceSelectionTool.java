package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class PlaneSliceSelectionTool {

	private PlaneSliceSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		Direction sliceFace = host.resolvePlaneSliceFace(face);
		host.mergeFromPlaneSlice(world, pos, sliceFace, host.resolveOperation(shiftDown));
	}
}
