package com.beatblock.ui.presenter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 选择属性面板当前视图状态（每帧由 Presenter 从 SelectionManager 读取）。
 */
public record SelectionPropertiesViewState(
	com.beatblock.selection.SelectionMode mode,
	com.beatblock.selection.SelectionOperation operation,
	int maxDistanceFromCamera,
	boolean selectionFillEnabled,
	boolean includeAir,
	int maxBlocks,
	int lineThicknessRadius,
	com.beatblock.selection.BrushShape brushShape,
	int sphereBrushRadius,
	Direction planeSliceFaceOverride,
	int maxMagicWandSpreadFromSeed,
	boolean connectedMatchFullState,
	int selectionCount,
	BlockPos boundingMin,
	BlockPos boundingMax,
	BlockPos boxFirstCorner,
	BlockPos lineFirstCorner,
	String lastMessage
) {}
