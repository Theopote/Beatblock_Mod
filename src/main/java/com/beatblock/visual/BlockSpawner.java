package com.beatblock.visual;

import com.beatblock.stage.StageZone;
import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 在舞台区域内生成/放置 BlockDisplay。
 */
public class BlockSpawner {

	public DisplayEntity.BlockDisplayEntity spawnAtStage(World world, StageZone stage, BlockState blockState,
	                                                     BlockDisplayPool pool) {
		if (world == null || stage == null || pool == null) return null;
		DisplayEntity.BlockDisplayEntity display = pool.obtain(world);
		if (display == null) return null;
		double x = stage.getCenterX();
		double y = stage.getCenterY();
		double z = stage.getCenterZ();
		display.setPosition(x, y, z);
		display.getData().setBlockState(blockState != null ? blockState : world.getBlockState(BlockPos.ORIGIN));
		return display;
	}

	public DisplayEntity.BlockDisplayEntity spawnAt(World world, double x, double y, double z, BlockState blockState,
	                                                BlockDisplayPool pool) {
		if (world == null || pool == null) return null;
		DisplayEntity.BlockDisplayEntity display = pool.obtain(world);
		if (display == null) return null;
		display.setPosition(x, y, z);
		display.getData().setBlockState(blockState != null ? blockState : world.getBlockState(BlockPos.ORIGIN));
		return display;
	}
}
