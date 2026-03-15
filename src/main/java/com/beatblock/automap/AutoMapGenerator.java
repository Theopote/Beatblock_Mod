package com.beatblock.automap;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.FrequencyEvent;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据频段事件与规则生成时间线动画事件，写入 TimelineModel.autoAnimationEvents。
 * 流程：Audio → Beatmap/Frequency → Energy Curve → Animation Generator → Timeline Events。
 */
public final class AutoMapGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(AutoMapGenerator.class);

	/** 无舞台时使用的默认目标 ID */
	public static final String DEFAULT_TARGET_ID = "default";

	/**
	 * 使用当前配置从时间线频段事件生成动画事件，追加到 autoAnimationEvents 并排序。
	 *
	 * @param timeline 时间线（读取 frequencyEvents，写入 autoAnimationEvents）
	 * @param config   规则与能量映射配置
	 * @param replace  若 true 先清空 autoAnimationEvents 再生成，否则追加
	 * @return 本次生成的事件数量
	 */
	public static int generate(TimelineModel timeline, AutoMapConfig config, boolean replace) {
		if (timeline == null || config == null) return 0;

		if (replace) {
			clearAutoAnimationEvents(timeline);
		}

		List<FrequencyEvent> events = timeline.getFrequencyEvents();
		if (events.isEmpty()) {
			LOGGER.info("BeatBlock Smart Auto Map: 无频段事件，请先导入音乐");
			return 0;
		}

		String targetId = resolveTargetObjectId();
		List<AutoMapRule> rules = config.getRules();
		double minGap = config.getMinGapSeconds();
		double lastTime = -minGap - 1;
		int count = 0;

		for (FrequencyEvent e : events) {
			if (e.getTimeSeconds() < lastTime + minGap) continue;
			AutoMapRule rule = findRule(rules, e.getBand(), e.getEnergy());
			if (rule == null) continue;

			Map<String, Object> params = new HashMap<>();
			if (rule.isUseEnergyForHeight()) {
				float h = e.getEnergy() * rule.getHeightMultiplier();
				params.put("height", h);
			}
			params.put("energy", e.getEnergy());

			TimelineAnimationEvent ev = new TimelineAnimationEvent(
				e.getTimeSeconds(),
				rule.getDurationSeconds(),
				rule.getAnimationTypeId(),
				targetId,
				e.getEnergy(),
				params
			);
			timeline.addAutoAnimationEvent(ev);
			lastTime = e.getTimeSeconds();
			count++;
		}

		timeline.sortAll();
		LOGGER.info("BeatBlock Smart Auto Map: 已生成 {} 个动画事件（目标: {}）", count, targetId);
		return count;
	}

	/**
	 * 清空时间线的自动动画事件（仅 autoAnimationEvents，不影响 blockAnimationEvents）。
	 */
	public static void clearAutoAnimationEvents(TimelineModel timeline) {
		if (timeline == null) return;
		// TimelineModel 没有 clearAutoAnimationEvents，需要加
		// 先读取现有接口：只有 addAutoAnimationEvent。所以需要在 TimelineModel 里加 clear 方法。
		// 我稍后加。这里先调用 timeline 的新方法。
		timeline.clearAutoAnimationEvents();
	}

	private static AutoMapRule findRule(List<AutoMapRule> rules, FrequencyBand band, float energy) {
		for (AutoMapRule r : rules) {
			if (r.getBand() == band && energy >= r.getMinEnergy()) {
				return r;
			}
		}
		return null;
	}

	private static String resolveTargetObjectId() {
		if (BeatBlock.stageManager != null && BeatBlock.stageManager.getCurrentStage().isPresent()) {
			return BeatBlock.stageManager.getCurrentStage().get().getId();
		}
		return DEFAULT_TARGET_ID;
	}
}
