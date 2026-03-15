package com.beatblock.visual;

import com.beatblock.stage.StageZone;
import net.minecraft.block.BlockState;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * 在舞台区域内生成/放置 BlockDisplay。
 * BlockDisplayEntity.BLOCK_STATE 为 private，通过 VarHandle 设置。
 */
public class BlockSpawner {

	private static final VarHandle BLOCK_STATE_HANDLE = findBlockStateHandle();

	private static VarHandle findBlockStateHandle() {
		try {
			var lookup = MethodHandles.privateLookupIn(DisplayEntity.BlockDisplayEntity.class, MethodHandles.lookup());
			return lookup.findStaticVarHandle(DisplayEntity.BlockDisplayEntity.class, "BLOCK_STATE", TrackedData.class);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get BLOCK_STATE handle", e);
		}
	}

	public DisplayEntity.BlockDisplayEntity spawnAtStage(World world, StageZone stage, BlockState blockState,
	                                                     BlockDisplayPool pool) {
		if (world == null || stage == null || pool == null) return null;
		DisplayEntity.BlockDisplayEntity display = pool.obtain(world);
		if (display == null) return null;
		double x = stage.getCenterX();
		double y = stage.getCenterY();
		double z = stage.getCenterZ();
		display.setPosition(x, y, z);
		setBlockState(display, blockState != null ? blockState : world.getBlockState(BlockPos.ORIGIN));
		return display;
	}

	public DisplayEntity.BlockDisplayEntity spawnAt(World world, double x, double y, double z, BlockState blockState,
	                                                BlockDisplayPool pool) {
		if (world == null || pool == null) return null;
		DisplayEntity.BlockDisplayEntity display = pool.obtain(world);
		if (display == null) return null;
		display.setPosition(x, y, z);
		setBlockState(display, blockState != null ? blockState : world.getBlockState(BlockPos.ORIGIN));
		return display;
	}

	@SuppressWarnings("unchecked")
	private static void setBlockState(DisplayEntity.BlockDisplayEntity display, BlockState state) {
		TrackedData<BlockState> key = (TrackedData<BlockState>) BLOCK_STATE_HANDLE.get();
		display.getDataTracker().set(key, state);
	}
}
