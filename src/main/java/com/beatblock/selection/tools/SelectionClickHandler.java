package com.beatblock.selection.tools;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

@FunctionalInterface
public interface SelectionClickHandler {

	void handle(SelectionToolHost host, World world, BlockPos pos, Direction face, boolean shiftDown);
}
