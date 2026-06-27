package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把一组「落点」（已经存在于世界中的真实方块坐标，作为节奏大师式判定目标）+ 一组命中时间，
 * 转换成对应数量的 {@code RhythmDrop} ANIMATE 事件：每个事件描述「一个纯视觉方块从高空精准
 * 下落，在指定的那一刻落到指定坐标，命中瞬间触发一次粒子」。
 * <p>
 * 全程不写真实世界方块——下落中的视觉方块走 ANIMATE 渲染层
 * （{@code com.beatblock.client.render.BeatBlockAnimatedBlocksRenderer}），落点本身就是已经
 * 存在的真实方块，本类只是在那个时间点、那个坐标触发一次视觉演出与一次粒子，不改变落点
 * 方块本身的任何状态。命中时间的来源建议用 {@link BeatGridPacing}（贴着真实检测到的节拍走），
 * 但本类对 {@link PacingStrategy} 的具体实现无感知，可传入任意排布策略算出的时间序列，
 * 也可以是创作者手动在时间轴上一个个拖出来的时间点。
 */
public final class RhythmDropEventFactory {

	/** 默认下落视觉时长（从出现到落地），秒。 */
	public static final double DEFAULT_FALL_DURATION_SECONDS = 1.0;
	/** 默认下落起始高度（相对落点向上偏移的方块数）。 */
	public static final double DEFAULT_FALL_HEIGHT_BLOCKS = 6.0;
	public static final String RHYTHM_DROP_ANIMATION_TYPE_ID = "RhythmDrop";

	private RhythmDropEventFactory() {}

	public static List<TimelineAnimationEvent> build(
		List<BlockPos> landingPositions,
		List<Double> landingTimesSeconds,
		String targetObjectId
	) {
		return build(landingPositions, landingTimesSeconds, targetObjectId,
			DEFAULT_FALL_DURATION_SECONDS, DEFAULT_FALL_HEIGHT_BLOCKS);
	}

	/**
	 * @param landingPositions 落点（按演出顺序排列；通常对应一段乐句里依次要「踩」的方块）
	 * @param landingTimesSeconds 每个落点对应的命中时间，与 landingPositions 按下标一一对应
	 * @param targetObjectId 落点共用的舞台对象 id（需要已注册进 {@code StageObjectSystem}；
	 *                       本类只用它取 center/name 做展示，实际动画目标方块由每个事件自带的
	 *                       singleBlock 参数决定，见 {@code BlockAnimationEngine.scheduleSingleBlockBurst}）
	 * @param fallDurationSeconds 下落视觉时长（&lt;=0 时使用默认值）
	 * @param fallHeightBlocks 下落起始高度（&lt;=0 时使用默认值）
	 */
	public static List<TimelineAnimationEvent> build(
		List<BlockPos> landingPositions,
		List<Double> landingTimesSeconds,
		String targetObjectId,
		double fallDurationSeconds,
		double fallHeightBlocks
	) {
		if (landingPositions == null || landingTimesSeconds == null) return List.of();
		int n = Math.min(landingPositions.size(), landingTimesSeconds.size());
		double duration = fallDurationSeconds > 0 ? fallDurationSeconds : DEFAULT_FALL_DURATION_SECONDS;
		double height = fallHeightBlocks > 0 ? fallHeightBlocks : DEFAULT_FALL_HEIGHT_BLOCKS;

		List<TimelineAnimationEvent> events = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			BlockPos pos = landingPositions.get(i);
			Double landingTime = landingTimesSeconds.get(i);
			if (pos == null || landingTime == null) continue;

			double startTime = Math.max(0.0, landingTime - duration);

			Map<String, Object> params = new LinkedHashMap<>();
			params.put("singleBlockX", pos.getX());
			params.put("singleBlockY", pos.getY());
			params.put("singleBlockZ", pos.getZ());
			params.put("meteorHeight", height);
			// 直线下落，不要 WORLD_TRAJECTORY 默认的横向散射（applyWorldTrajectory 缺省 2.5）——
			// 节奏大师式判定要求精确落在目标坐标上，不能像 Meteor 预设那样左右摆动。
			params.put("meteorScatter", 0.0);
			params.put("impactVfxKind", "rhythm_impact");

			events.add(new TimelineAnimationEvent(
				UUID.randomUUID().toString(),
				startTime,
				duration,
				RHYTHM_DROP_ANIMATION_TYPE_ID,
				targetObjectId,
				1f,
				params
			));
		}
		return events;
	}
}
