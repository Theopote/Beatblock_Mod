package com.beatblock.visual;

import com.beatblock.animation.AnimationInstance;
import com.beatblock.animation.TransformState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 根据 AnimationInstance 的当前状态更新 BlockDisplay 的变换（位置、旋转、缩放）。
 */
public class TransformUpdater {

	private Vec3d basePosition = Vec3d.ZERO;

	public void setBasePosition(Vec3d basePosition) {
		this.basePosition = basePosition;
	}

	public void tick(List<AnimationInstance> activeInstances, double currentTimeSeconds) {
		for (AnimationInstance inst : activeInstances) {
			Object target = inst.getDisplayTarget();
			if (!(target instanceof DisplayEntity.BlockDisplayEntity display)) continue;
			TransformState state = inst.sample(currentTimeSeconds);
			double x = basePosition.getX() + state.getX();
			double y = basePosition.getY() + state.getY();
			double z = basePosition.getZ() + state.getZ();
			display.setPosition(x, y, z);
		}
	}
}
