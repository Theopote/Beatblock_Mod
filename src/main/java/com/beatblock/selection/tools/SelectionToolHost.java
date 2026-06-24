package com.beatblock.selection.tools;

import com.beatblock.selection.SelectionOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** 选区工具处理所需的 Manager 能力（由 {@link com.beatblock.selection.BeatBlockSelectionManager} 实现）。 */
public interface SelectionToolHost {

	SelectionOperation getDefaultOperation();

	SelectionOperation resolveOperation(boolean shiftDown);

	void setMessage(String message);

	void handleDirectClick(World world, BlockPos pos, SelectionOperation op);

	BlockPos getBoxFirstCorner();

	void setBoxFirstCorner(BlockPos corner);

	void clearBoxFirstCorner();

	BlockPos getLineFirstCorner();

	void setLineFirstCorner(BlockPos corner);

	void clearLineFirstCorner();

	void mergeFromBox(World world, BlockPos cornerA, BlockPos cornerB, SelectionOperation op);

	void mergeFromLine(World world, BlockPos endA, BlockPos endB, SelectionOperation op);

	void mergeFromBrush(World world, BlockPos center, SelectionOperation op);

	void mergeFromConnected(World world, BlockPos start, SelectionOperation op);

	void mergeFromColumn(World world, BlockPos pos, SelectionOperation op);

	void mergeFromPlaneSlice(World world, BlockPos pos, Direction face, SelectionOperation op);

	void mergeFromSelectionWand(World world, BlockPos pos, SelectionOperation op);

	Direction resolvePlaneSliceFace(Direction hitFace);

	BlockPos getSelectionBoundingMin();

	BlockPos getSelectionBoundingMax();
}
