package com.beatblock.engine.influence;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.BlockStateResolver;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * APPEARANCE 通道：动画过中点后触发材质「闪烁」，实例结束前持续显示，结束后自然消失。
 * <p>
 * 不写真实世界 mutation——只在对应 {@link AnimatedBlock} 上设置
 * {@link AnimatedBlock#setAppearanceOverride(BlockState)}，由渲染层
 * （{@code BeatBlockAnimatedBlocksRenderer}）画成覆盖材质，原始世界方块完全不被触碰。
 * 这是有意的设计：闪烁是短促、高频触发的纯视觉反馈（如跑酷踩点），不需要也不应该
 * 真实持久化——如果走真实 setBlockState 再 revert，跑酷场景里密集的踩点会变成
 * 密集的真实区块写入，重新引入「真方块参与高频动画」的性能问题（建造揭示则不同，
 * 那是一次性、需要真正留存的场景，理应走真实写入，见 BuildLayer/BuildSequencer）。
 */
public final class AppearancePulseTracker {

	/** 已过触发点、正在持续闪烁中的实例 key 集合。 */
	private final Set<String> flashing = new HashSet<>();

	public static boolean crossedMidpoint(float t, float previousT) {
		return t >= 0.5f && previousT < 0.5f;
	}

	/**
	 * 每帧调用：若已过触发点（或本帧正好越过），为目标方块设置外观覆盖并（首次触发时）
	 * 派发一次 VFX；world 仅用于判断该位置是否为空气（跳过对着空气“闪烁”）。
	 */
	public void contribute(
		String instanceKey,
		EngineAnimationInstance instance,
		BlockInfluencePreset preset,
		InfluenceFrame frame,
		World world,
		float t,
		float previousT,
		EffectContext ctx
	) {
		if (instance == null || preset == null || frame == null || world == null || ctx == null) return;
		if (!presetHasAppearance(preset)) return;

		boolean alreadyFlashing = flashing.contains(instanceKey);
		boolean justCrossed = crossedMidpoint(t, previousT);
		if (!alreadyFlashing && !justCrossed) return;

		BlockState flash = BlockStateResolver.flashState(ctx.getExtraParams());
		boolean firstFrame = justCrossed && !alreadyFlashing;
		boolean vfx = firstFrame && vfxEnabled(ctx.getExtraParams());

		for (BlockPos pos : instance.getTarget().getBlocks()) {
			if (pos == null || world.getChunkAsView(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
			if (world.getBlockState(pos).isAir()) continue;
			BlockPos immutable = pos.toImmutable();

			AnimatedBlock block = frame.animatedBlockFor(immutable);
			block.setAppearanceOverride(flash);

			if (vfx) {
				frame.addVfxTrigger(new VfxTrigger(
					"appearance_flash",
					immutable,
					instance.getStartTimeSeconds() + t * Math.max(0.01, instance.getEndTimeSeconds() - instance.getStartTimeSeconds()),
					instance.getEnergy()
				));
			}
		}
		flashing.add(instanceKey);
	}

	public void clearInstance(String instanceKey) {
		flashing.remove(instanceKey);
	}

	private static boolean presetHasAppearance(BlockInfluencePreset preset) {
		return !preset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty();
	}

	private static boolean vfxEnabled(java.util.Map<String, Object> params) {
		if (params == null) return true;
		Object raw = params.get("vfxEnabled");
		if (raw == null) return true;
		if (raw instanceof Boolean b) return b;
		return !"false".equalsIgnoreCase(String.valueOf(raw).trim());
	}
}
