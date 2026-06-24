package com.beatblock.selection.tools;

import com.beatblock.selection.SelectionRegions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class SelectionWandSelectionTool {

	private SelectionWandSelectionTool() {}

	public static void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown) {
		BlockPos bMin = host.getSelectionBoundingMin();
		BlockPos bMax = host.getSelectionBoundingMax();
		if (bMin == null || bMax == null) {
			host.setMessage("选区魔棒：请先建立选区（需要有效包围盒）。");
			return;
		}
		if (!SelectionRegions.containsInBounds(pos, bMin, bMax)) {
			host.setMessage("选区魔棒：请点击当前选区包围盒内的方块。");
			return;
		}
		host.mergeFromSelectionWand(world, pos, host.resolveOperation(shiftDown));
	}
}
