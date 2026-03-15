package com.beatblock.visual;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * BlockDisplay 实体池：复用显示实体，减少生成/销毁。
 */
public class BlockDisplayPool {

	private final Deque<DisplayEntity.BlockDisplayEntity> available = new ArrayDeque<>();
	private final int maxPoolSize;

	public BlockDisplayPool() {
		this(64);
	}

	public BlockDisplayPool(int maxPoolSize) {
		this.maxPoolSize = Math.max(1, maxPoolSize);
	}

	public DisplayEntity.BlockDisplayEntity obtain(World world) {
		DisplayEntity.BlockDisplayEntity display = available.poll();
		if (display != null) {
			display.setNoGravity(true);
			display.setInvisible(false);
			return display;
		}
		DisplayEntity.BlockDisplayEntity newDisplay = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.COMMAND);
		if (newDisplay != null) {
			newDisplay.setNoGravity(true);
			if (world instanceof ServerWorld server) {
				server.spawnEntity(newDisplay);
			} else {
				world.spawnEntity(newDisplay);
			}
		}
		return newDisplay;
	}

	public void release(World world, DisplayEntity.BlockDisplayEntity display) {
		if (display == null) return;
		display.discard();
	}

	public void returnToPool(DisplayEntity.BlockDisplayEntity display) {
		if (display != null && available.size() < maxPoolSize) {
			display.setInvisible(true);
			display.setNoGravity(true);
			available.add(display);
		}
	}

	public int getAvailableCount() {
		return available.size();
	}
}
