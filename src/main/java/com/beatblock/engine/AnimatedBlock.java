package com.beatblock.engine;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 动画系统不直接修改世界方块，通过 AnimatedBlock 在逻辑/渲染层表示单块的状态。
 * 原始世界不动，动画在渲染层应用变换。
 */
public final class AnimatedBlock {

	private final BlockPos originalPos;
	private Vec3d position;
	private Vec3d velocity;
	private float rotationYaw;
	private float rotationPitch;
	private float scale;
	/**
	 * 可选：渲染层外观覆盖（如踩点闪烁）。非空时渲染器应该画这个 BlockState，
	 * 而不是该坐标真实世界方块的状态——原始方块本身完全没被改动，仅仅是「画的不一样」。
	 * 用于替代「真实 setBlockState 后再还原」这种会触发区块重建/光照重算的做法，
	 * 适合短促、高频触发的视觉反馈（如跑酷踩点变色），不适合需要真正持久化的场景（如建造揭示）。
	 */
	private BlockState appearanceOverride;

	public AnimatedBlock(BlockPos originalPos) {
		this.originalPos = originalPos != null ? originalPos : BlockPos.ORIGIN;
		this.position = new Vec3d(this.originalPos.getX() + 0.5, this.originalPos.getY(), this.originalPos.getZ() + 0.5);
		this.velocity = Vec3d.ZERO;
		this.rotationYaw = 0f;
		this.rotationPitch = 0f;
		this.scale = 1f;
	}

	public BlockPos getOriginalPos() {
		return originalPos;
	}

	public Vec3d getPosition() {
		return position;
	}

	public void setPosition(Vec3d position) {
		this.position = position != null ? position : this.position;
	}

	public void setPosition(double x, double y, double z) {
		this.position = new Vec3d(x, y, z);
	}

	public Vec3d getVelocity() {
		return velocity;
	}

	public void setVelocity(Vec3d velocity) {
		this.velocity = velocity != null ? velocity : Vec3d.ZERO;
	}

	public float getRotationYaw() {
		return rotationYaw;
	}

	public void setRotationYaw(float rotationYaw) {
		this.rotationYaw = rotationYaw;
	}

	public float getRotationPitch() {
		return rotationPitch;
	}

	public void setRotationPitch(float rotationPitch) {
		this.rotationPitch = rotationPitch;
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = Math.max(0.01f, scale);
	}

	public BlockState getAppearanceOverride() {
		return appearanceOverride;
	}

	/** null 表示不覆盖，渲染器应回退到该坐标真实世界方块的外观。 */
	public void setAppearanceOverride(BlockState appearanceOverride) {
		this.appearanceOverride = appearanceOverride;
	}

	/** 重置到原始位置与默认状态，用于每帧从原始坐标开始再叠加效果 */
	public void resetToOriginal() {
		position = new Vec3d(originalPos.getX() + 0.5, originalPos.getY(), originalPos.getZ() + 0.5);
		velocity = Vec3d.ZERO;
		rotationYaw = 0f;
		rotationPitch = 0f;
		scale = 1f;
		appearanceOverride = null;
	}
}
