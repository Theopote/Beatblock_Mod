package com.beatblock.testutil;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 基于 Mockito 的可变方块世界，供 {@link World} 依赖的单元测试使用。
 */
public final class StubBlockWorld {

	private StubBlockWorld() {}

	public record Handle(World world, Map<BlockPos, BlockState> blocks) {}

	public static Handle create() {
		return create(Map.of());
	}

	public static Handle create(Map<BlockPos, BlockState> initial) {
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		if (initial != null) {
			for (Map.Entry<BlockPos, BlockState> entry : initial.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) {
					blocks.put(entry.getKey().toImmutable(), entry.getValue());
				}
			}
		}

		World world = mock(World.class);
		Chunk chunk = mock(Chunk.class);
		when(world.isChunkLoaded(any(BlockPos.class))).thenReturn(true);
		when(world.getChunkAsView(anyInt(), anyInt())).thenReturn(chunk);
		when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
			BlockPos pos = invocation.getArgument(0);
			if (pos == null) return Blocks.AIR.getDefaultState();
			return blocks.getOrDefault(pos.toImmutable(), Blocks.AIR.getDefaultState());
		});
		doAnswer(invocation -> {
			BlockPos pos = invocation.getArgument(0);
			BlockState state = invocation.getArgument(1);
			if (pos != null && state != null) {
				blocks.put(pos.toImmutable(), state);
			}
			return true;
		}).when(world).setBlockState(any(BlockPos.class), any(BlockState.class), anyInt());

		return new Handle(world, blocks);
	}
}
