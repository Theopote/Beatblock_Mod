package com.beatblock.engine.influence;

import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * VFX 通道：动画接近结束时触发一次粒子（默认 t ≥ 0.92，可用事件参数 {@code impactThreshold} 覆盖）。
 * 典型用于"天降方块精准命中目标瞬间闪一下粒子"这类节奏反馈（见 {@code RhythmDrop} 预设）。
 * <p>
 * 与 {@link AppearancePulseTracker} 不同：VFX 本身就是瞬时事件，触发一次后不需要任何
 * "持续显示"或"结束还原"的状态——{@code fired} 集合只是为了保证同一个实例不会重复触发。
 */
public final class ImpactVfxTracker {

	private static final float DEFAULT_THRESHOLD = 0.92f;

	private final Set<String> fired = new HashSet<>();

	/** 每帧调用：若本帧正好越过触发阈值，对目标方块各派发一次 VFX。 */
	public void contribute(
		String instanceKey,
		EngineAnimationInstance instance,
		BlockInfluencePreset preset,
		InfluenceFrame frame,
		float t,
		float previousT,
		EffectContext ctx
	) {
		if (instance == null || preset == null || frame == null || ctx == null) return;
		if (!presetHasImpactVfx(preset)) return;
		if (fired.contains(instanceKey)) return;

		float threshold = (float) ctx.paramDouble("impactThreshold", DEFAULT_THRESHOLD);
		if (t < threshold || previousT >= threshold) return;

		String kind = stringParam(ctx, "impactVfxKind", "rhythm_impact");
		double eventTimeSeconds = instance.getStartTimeSeconds()
			+ t * Math.max(0.01, instance.getEndTimeSeconds() - instance.getStartTimeSeconds());

		for (BlockPos pos : instance.getTarget().getBlocks()) {
			if (pos == null) continue;
			frame.addVfxTrigger(new VfxTrigger(kind, pos.toImmutable(), eventTimeSeconds, instance.getEnergy()));
		}
		fired.add(instanceKey);
	}

	public void clearInstance(String instanceKey) {
		fired.remove(instanceKey);
	}

	private static boolean presetHasImpactVfx(BlockInfluencePreset preset) {
		return !preset.channelsFor(InfluenceDimension.VFX).isEmpty();
	}

	private static String stringParam(EffectContext ctx, String key, String fallback) {
		Object raw = ctx.getExtraParams().get(key);
		return raw != null ? String.valueOf(raw) : fallback;
	}
}
